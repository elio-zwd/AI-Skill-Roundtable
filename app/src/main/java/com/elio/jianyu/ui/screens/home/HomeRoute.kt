package com.elio.jianyu.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard

object HomeTestTags {
    const val SCREEN = "home_screen"
    const val QUESTION_PLACEHOLDER = "home_question_placeholder"
}

@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
) {
    HomeScreen(onOpenSettings = onOpenSettings)
}

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
) {
    JianyuPageShell(
        title = "首页",
        subtitle = "问题优先入口",
        onOpenSettings = onOpenSettings,
        modifier = Modifier.testTag(HomeTestTags.SCREEN),
    ) {
        JianyuStateCard(
            title = "先说说你想解决什么",
            message = "首页业务将在 PR09-06 接入。当前页面不会调用模型、创建议题或静默带入个人背景。",
            modifier = Modifier.testTag(HomeTestTags.QUESTION_PLACEHOLDER),
        )
        JianyuStateCard(
            title = "继续最近议题",
            message = "这里是稳定的页面接入点，当前仅展示导航壳占位状态。",
        )
    }
}
