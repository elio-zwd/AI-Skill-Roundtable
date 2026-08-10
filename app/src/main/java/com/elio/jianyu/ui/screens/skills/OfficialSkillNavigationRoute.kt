package com.elio.jianyu.ui.screens.skills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.catalog.OfficialSkillUseRequest
import com.elio.jianyu.ui.components.JianyuShellTestTags

/** PR09-04 拥有的根导航包装层；Catalog 页面内部状态仍由 PR09-05 公共 Route 管理。 */
@Composable
fun OfficialSkillNavigationRoute(
    repository: JianyuRepository,
    runtimeResult: OfficialSkillCatalogRuntimeResult,
    onOpenSettings: () -> Unit,
    onUseSkill: (OfficialSkillUseRequest) -> Unit,
    modifier: Modifier = Modifier,
    initialSkillId: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack == null) {
                    Spacer(Modifier.size(48.dp))
                } else {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(JianyuShellTestTags.PAGE_BACK_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = "Skill 目录",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "寻找适合此刻的能力",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag(JianyuShellTestTags.GLOBAL_SETTINGS_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "打开全局设置",
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        OfficialSkillCatalogRoute(
            repository = repository,
            runtimeResult = runtimeResult,
            initialSkillId = initialSkillId,
            onDismissInitialDetail = onBack,
            onUseSkill = onUseSkill,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}
