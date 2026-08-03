package com.elio.jianyu

import android.content.Context
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.RoomJianyuRepository
import com.elio.jianyu.data.RoundtableDatabase
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.catalog.createOfficialSkillCatalogRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App 组合层共享的见域运行时，保证页面与 Repository 使用同一官方 Skill 事实源。 */
data class JianyuAppRuntime(
    val repository: JianyuRepository,
    val officialSkillCatalogRuntimeResult: OfficialSkillCatalogRuntimeResult,
)

object JianyuAppRuntimeProvider {
    private val databaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var runtime: JianyuAppRuntime? = null

    fun get(context: Context): JianyuAppRuntime =
        runtime ?: synchronized(this) {
            runtime ?: create(context.applicationContext).also { created ->
                runtime = created
            }
        }

    private fun create(context: Context): JianyuAppRuntime {
        val catalogRuntimeResult = createOfficialSkillCatalogRuntime(context)
        val database = RoundtableDatabase.getDatabase(
            context = context,
            scope = databaseScope,
        )
        val repository = when (catalogRuntimeResult) {
            is OfficialSkillCatalogRuntimeResult.Success -> RoomJianyuRepository(
                database = database,
                officialSkillIdValidator = catalogRuntimeResult.runtime.validator,
            )
            is OfficialSkillCatalogRuntimeResult.Failure -> RoomJianyuRepository(
                database = database,
            )
        }
        return JianyuAppRuntime(
            repository = repository,
            officialSkillCatalogRuntimeResult = catalogRuntimeResult,
        )
    }
}
