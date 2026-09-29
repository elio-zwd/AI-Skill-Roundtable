package com.elio.jianyu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elio.jianyu.ui.theme.LocalReducedMotion
import com.elio.jianyu.ui.theme.skillRoundtableSpacing

object JianyuShellTestTags {
    const val GLOBAL_SETTINGS_BUTTON = "global_settings_button"
    const val PAGE_BACK_BUTTON = "page_back_button"
    const val TOP_BAR = "jianyu_top_bar"
    const val PAGE_TITLE = "jianyu_page_title"
}

enum class JianyuTopBarLevel {
    PRIMARY,
    SECONDARY,
}

object JianyuTopBarDefaults {
    val PrimaryMinHeight = 72.dp
    val SecondaryMinHeight = 56.dp
}

/**
 * 所有页面顶部栏共用的安全区与尺寸契约。系统栏 Insets 只在这里消费一次；
 * [heightIn] 保证正常字号下的稳定基线，同时允许放大字体自然撑高。
 */
@Composable
fun JianyuTopBarLayout(
    level: JianyuTopBarLevel,
    modifier: Modifier = Modifier,
    navigationContent: (@Composable () -> Unit)? = null,
    titleContent: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(JianyuShellTestTags.TOP_BAR),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = when (level) {
                            JianyuTopBarLevel.PRIMARY -> JianyuTopBarDefaults.PrimaryMinHeight
                            JianyuTopBarLevel.SECONDARY -> JianyuTopBarDefaults.SecondaryMinHeight
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigationContent?.invoke()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    content = titleContent,
                )
                actions()
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun JianyuTopBar(
    title: String,
    level: JianyuTopBarLevel,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    navigationContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    require(level != JianyuTopBarLevel.SECONDARY || onBack != null || navigationContent != null) {
        "二级页面必须提供顶部返回入口"
    }
    val resolvedNavigation: (@Composable () -> Unit)? = navigationContent ?: onBack?.let { callback ->
        {
            JianyuBackButton(onClick = callback)
        }
    }
    JianyuTopBarLayout(
        level = level,
        modifier = modifier,
        navigationContent = resolvedNavigation,
        titleContent = {
            Text(
                text = title,
                style = when (level) {
                    JianyuTopBarLevel.PRIMARY -> MaterialTheme.typography.headlineSmall
                    JianyuTopBarLevel.SECONDARY -> MaterialTheme.typography.titleLarge
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(JianyuShellTestTags.PAGE_TITLE),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = actions,
    )
}

@Composable
fun PrimaryTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    JianyuTopBar(
        title = title,
        level = JianyuTopBarLevel.PRIMARY,
        modifier = modifier,
        subtitle = subtitle,
        navigationContent = navigationContent,
        actions = actions,
    )
}

@Composable
fun SecondaryTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    JianyuTopBar(
        title = title,
        level = JianyuTopBarLevel.SECONDARY,
        modifier = modifier,
        subtitle = subtitle,
        onBack = onBack,
        actions = actions,
    )
}

@Composable
fun JianyuSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    inputModifier: Modifier = Modifier,
    placeholder: String = "搜索",
    clearButtonTestTag: String = "search_top_bar_clear_query",
    actions: @Composable RowScope.() -> Unit = {},
) {
    JianyuTopBarLayout(
        level = JianyuTopBarLevel.SECONDARY,
        modifier = modifier,
        navigationContent = { JianyuBackButton(onClick = onBack) },
        titleContent = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = inputModifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag(clearButtonTestTag),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清除搜索",
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        actions = actions,
    )
}

@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    inputModifier: Modifier = Modifier,
    placeholder: String = "搜索",
    clearButtonTestTag: String = "search_top_bar_clear_query",
    actions: @Composable RowScope.() -> Unit = {},
) {
    JianyuSearchTopBar(
        query = query,
        onQueryChange = onQueryChange,
        onBack = onBack,
        modifier = modifier,
        inputModifier = inputModifier,
        placeholder = placeholder,
        clearButtonTestTag = clearButtonTestTag,
        actions = actions,
    )
}

@Composable
private fun JianyuBackButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag(JianyuShellTestTags.PAGE_BACK_BUTTON),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
        )
    }
}

@Composable
fun JianyuSettingsAction(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag(JianyuShellTestTags.GLOBAL_SETTINGS_BUTTON),
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "打开全局设置",
        )
    }
}

/**
 * 遵循 /design-taste-frontend 规范：
 * 大气高阶弥散背景。在画布顶点呈现 0.05-0.08 超低饱和度柔光光晕，告别单调平铺白/黑底。
 */
@Composable
fun JianyuBackgroundAtmosphere(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val primaryGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    val secondaryGlow = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(primaryGlow, Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(x = 100f, y = 100f),
                    radius = 800f,
                ),
            )
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryGlow, Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(x = 1000f, y = 300f),
                    radius = 900f,
                ),
            ),
    ) {
        content()
    }
}

@Composable
fun JianyuPageShell(
    title: String,
    level: JianyuTopBarLevel,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    contentScrollable: Boolean = false,
    content: @Composable () -> Unit,
) {
    val spacing = MaterialTheme.skillRoundtableSpacing
    JianyuBackgroundAtmosphere(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (level) {
                JianyuTopBarLevel.PRIMARY -> PrimaryTopBar(
                    title = title,
                    subtitle = subtitle,
                    actions = actions,
                )

                JianyuTopBarLevel.SECONDARY -> SecondaryTopBar(
                    title = title,
                    subtitle = subtitle,
                    onBack = requireNotNull(onBack) {
                        "二级页面必须提供顶部返回入口"
                    },
                    actions = actions,
                )
            }

            val scrollState = rememberScrollState()
            val baseContentModifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(
                    horizontal = spacing.screenHorizontal,
                    vertical = spacing.small,
                )
            val contentModifier = if (contentScrollable) {
                baseContentModifier.verticalScroll(scrollState)
            } else {
                baseContentModifier
            }
            Column(
                modifier = contentModifier,
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                content()
            }
        }
    }
}

@Composable
fun JianyuStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionTestTag: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = MaterialTheme.skillRoundtableSpacing
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.compact),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                val buttonModifier = if (actionTestTag == null) {
                    Modifier
                } else {
                    Modifier.testTag(actionTestTag)
                }
                Button(
                    onClick = onAction,
                    modifier = buttonModifier.heightIn(min = 48.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun JianyuMetadataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.skillRoundtableSpacing
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(spacing.small))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun JianyuSkeletonShimmer(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 20.dp,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small,
) {
    val reducedMotion = LocalReducedMotion.current
    val alpha = if (reducedMotion) {
        0.4f
    } else {
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
        val alphaState = infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.6f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(
                    1000,
                    easing = androidx.compose.animation.core.EaseInOutSine,
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "shimmerAlpha",
        )
        alphaState.value
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.15f),
        shape = shape,
        content = {},
    )
}

@Composable
fun JianyuBadge(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = androidx.compose.foundation.shape.CircleShape,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
