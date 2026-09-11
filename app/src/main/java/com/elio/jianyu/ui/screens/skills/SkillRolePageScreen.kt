package com.elio.jianyu.ui.screens.skills

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.role.SkillRoleDiscoveryCategory
import com.elio.jianyu.ui.components.JianyuRoleAvatar
import com.elio.jianyu.ui.components.JianyuStateCard

private val RolePageBackground = Color(0xFFFCFBFE)

/**
 * UI-02 专用 Screen。
 *
 * 视觉以选定 A/B 原图为基线；legacy OfficialSkillCatalogScreen 继续服务旧目录兼容路径，
 * 这里不承载旧组合编辑和旧详情 Dialog。
 */
@Composable
internal fun SkillRolePageScreen(
    uiState: OfficialSkillCatalogUiState,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RolePageBackground)
            .testTag(OfficialSkillCatalogTestTags.ROOT),
    ) {
        when {
            uiState.isLoading -> {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag(OfficialSkillCatalogTestTags.LOADING),
                )
                Spacer(Modifier.weight(1f))
            }
            uiState.catalogError != null -> {
                Spacer(Modifier.height(24.dp))
                JianyuStateCard(
                    title = "角色目录暂不可用",
                    message = uiState.catalogError,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag(OfficialSkillCatalogTestTags.ERROR),
                )
            }
            uiState.roleCatalog == null -> {
                Spacer(Modifier.height(24.dp))
                JianyuStateCard(
                    title = "角色目录暂不可用",
                    message = "角色展示数据尚未完成加载。",
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag(OfficialSkillCatalogTestTags.ERROR),
                )
            }
            else -> SkillRolePageContent(
                uiState = uiState,
                roleCatalog = uiState.roleCatalog,
                onEvent = onEvent,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (uiState.filterDialogVisible) {
        SkillRoleFilterDialog(filters = uiState.filters, onEvent = onEvent)
    }

    uiState.message?.let { message ->
        OfficialSkillCatalogMessageDialog(
            message = message,
            onDismiss = { onEvent(OfficialSkillCatalogEvent.DismissMessage) },
        )
    }
}

@Composable
private fun SkillRolePageContent(
    uiState: OfficialSkillCatalogUiState,
    roleCatalog: SkillRoleCatalogUiState,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategoryName by rememberSaveable(roleCatalog.selectedCategory) {
        mutableStateOf(roleCatalog.selectedCategory?.name.orEmpty())
    }
    val selectedCategory = selectedCategoryName
        .takeIf(String::isNotBlank)
        ?.let { encoded -> runCatching { SkillRoleDiscoveryCategory.valueOf(encoded) }.getOrNull() }
    val favoritesOnly = uiState.section == OfficialSkillCatalogSection.FAVORITES
    val categoryRoles = roleCatalog.visibleRoles.filter { role ->
        selectedCategory == null || role.primaryDiscoveryCategory == selectedCategory
    }
    val displayedRoles = if (favoritesOnly) {
        categoryRoles.filter(SkillRoleCardUi::isFavorite)
    } else {
        categoryRoles
    }
    val displayedIds = displayedRoles.mapTo(linkedSetOf(), SkillRoleCardUi::skillId)
    val featuredRoles = roleCatalog.featuredRoles.filter { it.skillId in displayedIds }
    val recentRoles = roleCatalog.recentRoles.filter { it.skillId in displayedIds }.take(2)

    RolePageHeader(
        favoritesOnly = favoritesOnly,
        onToggleFavorites = {
            onEvent(
                OfficialSkillCatalogEvent.SectionChanged(
                    if (favoritesOnly) OfficialSkillCatalogSection.DISCOVER
                    else OfficialSkillCatalogSection.FAVORITES,
                ),
            )
        },
        onOpenFilters = { onEvent(OfficialSkillCatalogEvent.FilterDialogChanged(true)) },
    )

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag(OfficialSkillCatalogTestTags.LIST),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "search_and_categories") {
            RoleSearchAndCategories(
                query = uiState.query,
                selectedCategory = selectedCategory,
                onQueryChanged = { onEvent(OfficialSkillCatalogEvent.SearchChanged(it)) },
                onCategorySelected = { category -> selectedCategoryName = category?.name.orEmpty() },
            )
        }

        if (favoritesOnly) {
            item(key = "favorites_title") { RoleSectionTitle("收藏角色") }
            if (displayedRoles.isEmpty()) {
                item(key = "favorites_empty") {
                    SkillRoleEmptyState(
                        title = "还没有符合条件的收藏角色",
                        message = "可回到全部角色收藏常用角色，或调整当前搜索与筛选。",
                    )
                }
            } else if (selectedCategory == null) {
                items(displayedRoles, key = { "favorite_${it.skillId}" }) { role ->
                    RoleListRow(role, onEvent)
                }
            } else {
                item(key = "favorites_grid") { RoleGrid(displayedRoles, onEvent) }
            }
        } else if (uiState.query.isNotBlank()) {
            item(key = "search_title") { RoleSectionTitle("搜索结果") }
            if (displayedRoles.isEmpty()) {
                item(key = "search_empty") {
                    SkillRoleEmptyState(
                        title = "没有找到匹配角色",
                        message = "可缩短关键词、切换分类或清除筛选条件。",
                    )
                }
            } else if (selectedCategory == null) {
                items(displayedRoles, key = { "search_${it.skillId}" }) { role ->
                    RoleListRow(role, onEvent)
                }
            } else {
                item(key = "search_grid") { RoleGrid(displayedRoles, onEvent) }
            }
        } else if (selectedCategory == null) {
            if (featuredRoles.isNotEmpty()) {
                item(key = "featured_title") { RoleSectionTitle("推荐角色") }
                item(key = "featured_hero") {
                    RoleFeaturedHero(
                        role = featuredRoles.first(),
                        onOpenDetail = {
                            onEvent(OfficialSkillCatalogEvent.OpenDetail(featuredRoles.first().skillId))
                        },
                        onToggleFavorite = {
                            onEvent(OfficialSkillCatalogEvent.ToggleFavorite(featuredRoles.first().skillId))
                        },
                    )
                }
                if (featuredRoles.size > 1) {
                    item(key = "featured_secondary") {
                        RoleFeaturedSecondaryRow(
                            roles = featuredRoles.drop(1).take(2),
                            onOpenDetail = { onEvent(OfficialSkillCatalogEvent.OpenDetail(it)) },
                            onToggleFavorite = { onEvent(OfficialSkillCatalogEvent.ToggleFavorite(it)) },
                        )
                    }
                }
            }

            if (recentRoles.isNotEmpty()) {
                item(key = "recent_title") {
                    RoleSectionTitle("最近使用", Modifier.padding(top = 8.dp))
                }
                item(key = "recent_cards") {
                    RoleRecentCards(recentRoles, onEvent)
                }
            }

            item(key = "all_title") {
                RoleSectionTitle("全部角色", Modifier.padding(top = 8.dp))
            }
            if (displayedRoles.isEmpty()) {
                item(key = "all_empty") {
                    SkillRoleEmptyState(
                        title = "没有符合筛选条件的角色",
                        message = "可清除筛选后查看完整角色目录。",
                    )
                }
            } else {
                items(displayedRoles, key = { "all_${it.skillId}" }) { role ->
                    RoleListRow(role, onEvent)
                }
            }
        } else {
            item(key = "category_info") { RoleCategoryInfoCard(selectedCategory) }
            if (displayedRoles.isEmpty()) {
                item(key = "category_empty") {
                    SkillRoleEmptyState(
                        title = "这个分类下暂无匹配角色",
                        message = "当前搜索或筛选条件没有结果；不会自动切回全部。",
                    )
                }
            } else {
                item(key = "category_grid") { RoleGrid(displayedRoles, onEvent) }
            }
        }

        item(key = "bottom_space") { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun RolePageHeader(
    favoritesOnly: Boolean,
    onToggleFavorites: () -> Unit,
    onOpenFilters: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Skill 角色",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "和不同的思考方式对话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onToggleFavorites,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (favoritesOnly) "查看全部角色" else "查看收藏角色",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        TextButton(
            onClick = onOpenFilters,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(OfficialSkillCatalogTestTags.FILTER_BUTTON),
        ) {
            Text("筛选")
        }
    }
}

@Composable
private fun RoleSearchAndCategories(
    query: String,
    selectedCategory: SkillRoleDiscoveryCategory?,
    onQueryChanged: (String) -> Unit,
    onCategorySelected: (SkillRoleDiscoveryCategory?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            singleLine = true,
            placeholder = { Text("搜索角色、能力或问题") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag(OfficialSkillCatalogTestTags.SEARCH),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            skillRoleDiscoveryCategories.forEach { category ->
                FilterChip(
                    selected = category == selectedCategory,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category.displayName()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.heightIn(min = 40.dp),
                )
            }
        }
        Text(
            text = "按角色最稳定的思考方式分类",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoleSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
private fun RoleFeaturedHero(
    role: SkillRoleCardUi,
    onOpenDetail: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .testTag(OfficialSkillCatalogTestTags.skill(role.skillId)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = roleVisualContainerColor(role.primaryDiscoveryCategory),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 176.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoleIdentityVisual(
                role = role,
                modifier = Modifier
                    .width(140.dp)
                    .height(176.dp),
                cornerRadius = 24.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = role.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (role.isPersonSimulation) {
                            Text(
                                text = "AI 模拟角色",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    RoleFavoriteButton(role, onToggleFavorite)
                }
                Text(
                    text = role.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoleBadge(role.primaryDiscoveryCategory.displayName())
                    RoleBadge(role.visualTypeLabel())
                }
                Text(
                    text = "查看角色",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RoleFeaturedSecondaryRow(
    roles: List<SkillRoleCardUi>,
    onOpenDetail: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val singleColumn = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.15f
        if (singleColumn) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                roles.forEach { role ->
                    RoleFeatureMiniCard(
                        role = role,
                        onOpenDetail = { onOpenDetail(role.skillId) },
                        onToggleFavorite = { onToggleFavorite(role.skillId) },
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                roles.forEach { role ->
                    RoleFeatureMiniCard(
                        role = role,
                        onOpenDetail = { onOpenDetail(role.skillId) },
                        onToggleFavorite = { onToggleFavorite(role.skillId) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (roles.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RoleFeatureMiniCard(
    role: SkillRoleCardUi,
    onOpenDetail: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(118.dp)
            .clickable(onClick = onOpenDetail)
            .testTag(OfficialSkillCatalogTestTags.skill(role.skillId)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = roleVisualContainerColor(role.primaryDiscoveryCategory),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.fillMaxSize()) {
            RoleIdentityVisual(
                role = role,
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight(),
                cornerRadius = 20.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = role.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    RoleFavoriteButton(role, onToggleFavorite, size = 36.dp)
                }
                Text(
                    text = role.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (role.isPersonSimulation) {
                    Text(
                        text = "AI 模拟角色",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleRecentCards(
    roles: List<SkillRoleCardUi>,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val singleColumn = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.15f
        if (singleColumn || roles.size == 1) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                roles.forEach { role -> RoleListRow(role, onEvent) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                roles.forEach { role ->
                    RoleFeatureMiniCard(
                        role = role,
                        onOpenDetail = { onEvent(OfficialSkillCatalogEvent.OpenDetail(role.skillId)) },
                        onToggleFavorite = { onEvent(OfficialSkillCatalogEvent.ToggleFavorite(role.skillId)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleCategoryInfoCard(category: SkillRoleDiscoveryCategory) {
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.45f)
                    .height(112.dp),
            ) {
                val p1 = Offset(size.width * 0.18f, size.height * 0.64f)
                val p2 = Offset(size.width * 0.48f, size.height * 0.34f)
                val p3 = Offset(size.width * 0.78f, size.height * 0.58f)
                val p4 = Offset(size.width * 0.62f, size.height * 0.82f)
                drawLine(lineColor, p1, p2, strokeWidth = 2f)
                drawLine(lineColor, p2, p3, strokeWidth = 2f)
                drawLine(lineColor, p2, p4, strokeWidth = 2f)
                listOf(p1, p2, p3, p4).forEachIndexed { index, point ->
                    drawCircle(
                        color = lineColor.copy(alpha = if (index == 1) 0.7f else 0.45f),
                        radius = if (index == 1) 10f else 6f,
                        center = point,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = category.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = category.description(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(0.72f),
                )
            }
        }
    }
}

@Composable
private fun RoleGrid(
    roles: List<SkillRoleCardUi>,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val singleColumn = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.15f
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (singleColumn) {
                roles.forEach { role ->
                    RoleGridCard(role, onEvent)
                }
            } else {
                roles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { role ->
                            RoleGridCard(role, onEvent, Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleGridCard(
    role: SkillRoleCardUi,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clickable { onEvent(OfficialSkillCatalogEvent.OpenDetail(role.skillId)) }
            .testTag(OfficialSkillCatalogTestTags.skill(role.skillId)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp),
            ) {
                RoleIdentityVisual(
                    role = role,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 20.dp,
                )
                RoleFavoriteButton(
                    role = role,
                    onToggleFavorite = {
                        onEvent(OfficialSkillCatalogEvent.ToggleFavorite(role.skillId))
                    },
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = role.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = role.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (role.isPersonSimulation) RoleBadge("AI 模拟角色")
                    else RoleBadge(role.visualTypeLabel())
                }
            }
        }
    }
}

@Composable
private fun RoleListRow(
    role: SkillRoleCardUi,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEvent(OfficialSkillCatalogEvent.OpenDetail(role.skillId)) }
            .testTag(OfficialSkillCatalogTestTags.skill(role.skillId)),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            RoleIdentityVisual(
                role = role,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp)),
                cornerRadius = 16.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = role.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (role.isPersonSimulation) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "AI 模拟角色",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = role.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RoleFavoriteButton(
                role = role,
                onToggleFavorite = {
                    onEvent(OfficialSkillCatalogEvent.ToggleFavorite(role.skillId))
                },
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

@Composable
private fun RoleIdentityVisual(
    role: SkillRoleCardUi,
    modifier: Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp,
) {
    if (role.isPersonSimulation) {
        JianyuRoleAvatar(
            name = role.name,
            assetPath = role.visualAvatarPath(),
            modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
            fallbackContainerColor = roleVisualContainerColor(role.primaryDiscoveryCategory),
            fallbackContentColor = roleVisualContentColor(role.primaryDiscoveryCategory),
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(roleVisualContainerColor(role.primaryDiscoveryCategory)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = role.name.take(2),
                color = roleVisualContentColor(role.primaryDiscoveryCategory),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RoleBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun RoleFavoriteButton(
    role: SkillRoleCardUi,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    IconButton(
        onClick = onToggleFavorite,
        modifier = modifier
            .size(size)
            .testTag(OfficialSkillCatalogTestTags.favorite(role.skillId)),
    ) {
        Icon(
            imageVector = if (role.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (role.isFavorite) "取消收藏 ${role.name}" else "收藏 ${role.name}",
            tint = if (role.isFavorite) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun SkillRoleCardUi.visualAvatarPath(): String? =
    avatarAssetPath ?: if (isPersonSimulation) "avatars/$skillId.jpg" else null

private fun SkillRoleCardUi.visualTypeLabel(): String = when (primaryType) {
    OfficialSkillPrimaryType.PERSON_PERSPECTIVE -> "人物视角"
    OfficialSkillPrimaryType.PROFESSIONAL_ADVISOR -> "专业顾问"
    OfficialSkillPrimaryType.TASK_ASSISTANT -> "任务助手"
    OfficialSkillPrimaryType.WORKFLOW_CAPABILITY -> "工作流"
}

@Composable
private fun roleVisualContainerColor(category: SkillRoleDiscoveryCategory): Color = when (category) {
    SkillRoleDiscoveryCategory.THINKING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    SkillRoleDiscoveryCategory.CAREER -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    SkillRoleDiscoveryCategory.RESEARCH_LEARNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.48f)
    SkillRoleDiscoveryCategory.PRODUCT_CREATION -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    SkillRoleDiscoveryCategory.COMMUNICATION -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
    SkillRoleDiscoveryCategory.OFFICE_TASKS -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
    SkillRoleDiscoveryCategory.LIFE_TOOLS -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.50f)
}

@Composable
private fun roleVisualContentColor(category: SkillRoleDiscoveryCategory): Color = when (category) {
    SkillRoleDiscoveryCategory.THINKING,
    SkillRoleDiscoveryCategory.PRODUCT_CREATION -> MaterialTheme.colorScheme.onPrimaryContainer
    SkillRoleDiscoveryCategory.CAREER,
    SkillRoleDiscoveryCategory.COMMUNICATION -> MaterialTheme.colorScheme.onSecondaryContainer
    SkillRoleDiscoveryCategory.RESEARCH_LEARNING,
    SkillRoleDiscoveryCategory.LIFE_TOOLS -> MaterialTheme.colorScheme.onTertiaryContainer
    SkillRoleDiscoveryCategory.OFFICE_TASKS -> MaterialTheme.colorScheme.onSurfaceVariant
}
