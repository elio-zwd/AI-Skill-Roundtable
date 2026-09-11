package com.elio.jianyu.ui.screens.mine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elio.jianyu.ui.components.UserAvatar

@Composable
fun MineScreen(
    uiState: MineUiState,
    onOpenSettings: () -> Unit,
    onOpenAiManagement: () -> Unit,
    onOpenTelemetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(MineTestTags.SCREEN),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            MineHeader(onOpenSettings = onOpenSettings)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    PersonalBackgroundHero(uiState = uiState)
                }
                item {
                    Text(
                        text = "快捷控制",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                item {
                    QuickControls(
                        uiState = uiState,
                        onOpenAiManagement = onOpenAiManagement,
                        onOpenTelemetry = onOpenTelemetry,
                    )
                }
                item {
                    Text(
                        text = "应用偏好",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                item {
                    PreferenceGroup(onOpenSettings = onOpenSettings)
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }
        }
    }
}

@Composable
private fun MineHeader(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "我的",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .testTag(MineTestTags.SETTINGS_BUTTON),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
            )
        }
    }
}

@Composable
private fun PersonalBackgroundHero(uiState: MineUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MineTestTags.PERSONAL_BACKGROUND_HERO),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier.size(92.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    UserAvatar(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    )
                    IconButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag(MineTestTags.AVATAR_SWITCH_UNAVAILABLE),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "头像切换（待产品定义）",
                            modifier = Modifier.scale(0.72f),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "个人背景",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "让 Skill 角色更了解你的目标、限制与偏好。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.personalBackgroundStatus,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.testTag(MineTestTags.PERSONAL_BACKGROUND_ACTION),
                    ) {
                        Text("查看与编辑")
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("职业目标", "可用时间", "表达偏好").forEach { label ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickControls(
    uiState: MineUiState,
    onOpenAiManagement: () -> Unit,
    onOpenTelemetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickControlCard(
                title = "AI 管理",
                subtitle = uiState.aiManagementStatus,
                icon = Icons.Default.Settings,
                enabled = true,
                onClick = onOpenAiManagement,
                modifier = Modifier
                    .weight(1f)
                    .testTag(MineTestTags.AI_MANAGEMENT_CARD),
            )
            QuickControlCard(
                title = "数据与隐私",
                subtitle = "当前不可用",
                icon = Icons.Default.Info,
                enabled = false,
                onClick = {},
                modifier = Modifier
                    .weight(1f)
                    .testTag(MineTestTags.DATA_PRIVACY_CARD),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickControlCard(
                title = "备份与恢复",
                subtitle = "当前不可用",
                icon = Icons.Default.List,
                enabled = false,
                onClick = {},
                modifier = Modifier
                    .weight(1f)
                    .testTag(MineTestTags.BACKUP_RESTORE_CARD),
            )
            QuickControlCard(
                title = "遥测与诊断",
                subtitle = uiState.telemetryStatus,
                icon = Icons.Default.Info,
                enabled = true,
                onClick = onOpenTelemetry,
                modifier = Modifier
                    .weight(1f)
                    .testTag(MineTestTags.TELEMETRY_CARD),
            )
        }
    }
}

@Composable
private fun QuickControlCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(124.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
            },
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PreferenceGroup(onOpenSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            PreferenceRow(
                title = "设置",
                subtitle = "应用、AI 与诊断配置",
                icon = Icons.Default.Settings,
                enabled = true,
                onClick = onOpenSettings,
                modifier = Modifier.testTag(MineTestTags.SETTINGS_ENTRY),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceRow(
                title = "关于见域",
                subtitle = "当前不可用",
                icon = Icons.Default.Info,
                enabled = false,
                onClick = {},
                modifier = Modifier.testTag(MineTestTags.ABOUT_ENTRY),
            )
        }
    }
}

@Composable
private fun PreferenceRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
