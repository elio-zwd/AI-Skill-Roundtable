package com.elio.jianyu.ui.screens.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.BuildConfig
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard

object AboutTestTags {
    const val SCREEN = "about_screen"
    const val IDENTITY = "about_identity"
}

@Composable
fun AboutRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: BuildConfig.VERSION_NAME
    JianyuPageShell(
        title = "关于见域",
        onBack = onBack,
        contentScrollable = true,
        modifier = Modifier.testTag(AboutTestTags.SCREEN),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("见域", style = MaterialTheme.typography.displaySmall)
            Text("看见更多观点，打开认知边界", color = MaterialTheme.colorScheme.primary)
            Text("版本 $version", color = MaterialTheme.colorScheme.onSurfaceVariant)
            JianyuStateCard(
                title = "关于见域",
                message = "见域是一款以 AI Skill 角色对话为核心的个人思考与行动应用。资料、个人背景和成果由你主动管理。",
            )
            JianyuStateCard(
                title = "关于 Skill 角色",
                message = "真实人物型角色为 AI 模拟角色，基于可获得资料构建，不代表本人，也不保证复现本人当前或完整观点。",
                modifier = Modifier.testTag(AboutTestTags.IDENTITY),
            )
            JianyuStateCard(
                title = "本地应用信息",
                message = "applicationId：com.elio.jianyu\n完整 API Key 不会在本页面显示；旧包 com.elio.skillroundtable 的数据不会自动迁移。",
            )
            Text("开源许可与第三方内容说明将在发布包中随附；当前版本不提供云端账号或自动同步。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
