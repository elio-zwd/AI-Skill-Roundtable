package com.elio.jianyu.skill.catalog

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface OfficialSkillPreferences {
    val favoriteIds: StateFlow<Set<String>>
    val recentUses: StateFlow<List<RecentOfficialSkillUse>>

    suspend fun setFavorite(skillId: String, favorite: Boolean): Boolean

    suspend fun recordSkillUsed(skillId: String, usedAt: Long): Boolean

    /** 清空最近使用记录；不影响收藏、会话历史与当前参与者。 */
    suspend fun clearRecentUses(): Boolean

    /** 删除全部本地数据时同时清空收藏与最近使用，并同步当前进程状态。 */
    suspend fun clearAll(): Boolean

    /** 查看详情不等于真正进入使用流程，故该事件不得写入最近使用。 */
    suspend fun onSkillDetailViewed(skillId: String) = Unit
}

class InMemoryOfficialSkillPreferences(
    private val catalog: OfficialSkillCatalog,
    initialFavoriteIds: Set<String> = emptySet(),
    initialRecentUses: List<RecentOfficialSkillUse> = emptyList(),
    private val maxRecent: Int = DEFAULT_MAX_RECENT,
) : OfficialSkillPreferences {
    private val _favoriteIds = MutableStateFlow(initialFavoriteIds.validIds(catalog))
    override val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _recentUses = MutableStateFlow(
        initialRecentUses.normalized(catalog, maxRecent),
    )
    override val recentUses: StateFlow<List<RecentOfficialSkillUse>> = _recentUses.asStateFlow()

    override suspend fun setFavorite(skillId: String, favorite: Boolean): Boolean {
        if (!catalog.containsOfficialId(skillId)) return false
        _favoriteIds.value = if (favorite) {
            _favoriteIds.value + skillId
        } else {
            _favoriteIds.value - skillId
        }
        return true
    }

    override suspend fun recordSkillUsed(skillId: String, usedAt: Long): Boolean {
        if (!catalog.containsOfficialId(skillId) || usedAt <= 0L) return false
        _recentUses.value = (
            listOf(RecentOfficialSkillUse(skillId, usedAt)) +
                _recentUses.value.filterNot { it.skillId == skillId }
            ).normalized(catalog, maxRecent)
        return true
    }

    override suspend fun clearRecentUses(): Boolean {
        _recentUses.value = emptyList()
        return true
    }

    override suspend fun clearAll(): Boolean {
        _favoriteIds.value = emptySet()
        _recentUses.value = emptyList()
        return true
    }

    companion object {
        const val DEFAULT_MAX_RECENT = 20
    }
}

class SharedPreferencesOfficialSkillPreferences(
    context: Context,
    private val catalog: OfficialSkillCatalog,
    private val maxRecent: Int = InMemoryOfficialSkillPreferences.DEFAULT_MAX_RECENT,
) : OfficialSkillPreferences {
    private val lock = Any()
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    private val _favoriteIds = MutableStateFlow(loadFavoriteIds())
    override val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _recentUses = MutableStateFlow(loadRecentUses())
    override val recentUses: StateFlow<List<RecentOfficialSkillUse>> = _recentUses.asStateFlow()

    override suspend fun setFavorite(skillId: String, favorite: Boolean): Boolean {
        if (!catalog.containsOfficialId(skillId)) return false
        return synchronized(lock) {
            val updated = if (favorite) {
                _favoriteIds.value + skillId
            } else {
                _favoriteIds.value - skillId
            }
            val committed = preferences.edit()
                .putStringSet(KEY_FAVORITE_IDS, updated)
                .commit()
            if (committed) _favoriteIds.value = updated
            committed
        }
    }

    override suspend fun recordSkillUsed(skillId: String, usedAt: Long): Boolean {
        if (!catalog.containsOfficialId(skillId) || usedAt <= 0L) return false
        return synchronized(lock) {
            val updated = (
                listOf(RecentOfficialSkillUse(skillId, usedAt)) +
                    _recentUses.value.filterNot { it.skillId == skillId }
                ).normalized(catalog, maxRecent)
            val committed = preferences.edit()
                .putString(KEY_RECENT_USES, json.encodeToString(updated))
                .commit()
            if (committed) _recentUses.value = updated
            committed
        }
    }

    override suspend fun clearRecentUses(): Boolean {
        return synchronized(lock) {
            val committed = preferences.edit()
                .remove(KEY_RECENT_USES)
                .commit()
            if (committed) _recentUses.value = emptyList()
            committed
        }
    }

    override suspend fun clearAll(): Boolean {
        return synchronized(lock) {
            val committed = preferences.edit().clear().commit()
            if (committed) {
                _favoriteIds.value = emptySet()
                _recentUses.value = emptyList()
            }
            committed
        }
    }

    private fun loadFavoriteIds(): Set<String> = preferences
        .getStringSet(KEY_FAVORITE_IDS, emptySet())
        .orEmpty()
        .validIds(catalog)

    private fun loadRecentUses(): List<RecentOfficialSkillUse> {
        val encoded = preferences.getString(KEY_RECENT_USES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<RecentOfficialSkillUse>>(encoded)
                .normalized(catalog, maxRecent)
        }.getOrDefault(emptyList())
    }

    companion object {
        internal const val PREFERENCES_NAME = "official_skill_catalog_preferences_v1"

        internal fun clearStored(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        const val KEY_FAVORITE_IDS = "favorite_official_skill_ids"
        const val KEY_RECENT_USES = "recent_official_skill_uses"
    }
}

private fun Set<String>.validIds(catalog: OfficialSkillCatalog): Set<String> =
    filterTo(linkedSetOf(), catalog::containsOfficialId)

private fun List<RecentOfficialSkillUse>.normalized(
    catalog: OfficialSkillCatalog,
    maxRecent: Int,
): List<RecentOfficialSkillUse> {
    if (maxRecent <= 0) return emptyList()
    val seen = mutableSetOf<String>()
    return asSequence()
        .filter { it.usedAt > 0L && catalog.containsOfficialId(it.skillId) }
        .sortedWith(compareByDescending<RecentOfficialSkillUse> { it.usedAt }.thenBy { it.skillId })
        .filter { seen.add(it.skillId) }
        .take(maxRecent)
        .toList()
}


internal fun clearStoredOfficialSkillPreferences(context: Context): Boolean =
    SharedPreferencesOfficialSkillPreferences.clearStored(context)
