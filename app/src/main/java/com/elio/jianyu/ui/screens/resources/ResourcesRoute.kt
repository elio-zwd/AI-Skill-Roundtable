package com.elio.jianyu.ui.screens.resources

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard
import com.elio.jianyu.ui.navigation.ResourceTab

object ResourcesTestTags {
    const val SCREEN = "resources_screen"
    const val MATERIALS_TAB = "resources_tab_materials"
    const val ARTIFACTS_TAB = "resources_tab_artifacts"
    const val EMPTY_STATE = "resources_empty_state"
}

@Composable
fun ResourcesRoute(
    initialTab: ResourceTab,
    onOpenSettings: () -> Unit,
) {
    var selectedRouteValue by rememberSaveable(initialTab.routeValue) {
        mutableStateOf(initialTab.routeValue)
    }
    ResourcesScreen(
        selectedTab = ResourceTab.fromRouteValue(selectedRouteValue),
        onSelectTab = { tab -> selectedRouteValue = tab.routeValue },
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun ResourcesScreen(
    selectedTab: ResourceTab,
    onSelectTab: (ResourceTab) -> Unit,
    onOpenSettings: () -> Unit,
) {
    JianyuPageShell(
        title = "资料与成果",
        subtitle = "保留来源，回到对应议题与阶段",
        onOpenSettings = onOpenSettings,
        modifier = Modifier.testTag(ResourcesTestTags.SCREEN),
    ) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == ResourceTab.MATERIALS,
                onClick = { onSelectTab(ResourceTab.MATERIALS) },
                text = { Text("资料") },
                modifier = Modifier.testTag(ResourcesTestTags.MATERIALS_TAB),
            )
            Tab(
                selected = selectedTab == ResourceTab.ARTIFACTS,
                onClick = { onSelectTab(ResourceTab.ARTIFACTS) },
                text = { Text("成果") },
                modifier = Modifier.testTag(ResourcesTestTags.ARTIFACTS_TAB),
            )
        }

        when (selectedTab) {
            ResourceTab.MATERIALS -> JianyuStateCard(
                title = "暂无资料",
                message = "资料业务将在 PR09-09 接入。当前页面不会创建资料或读取 ResourceLifecycleDao。",
                modifier = Modifier.testTag(ResourcesTestTags.EMPTY_STATE),
            )
            ResourceTab.ARTIFACTS -> JianyuStateCard(
                title = "暂无成果",
                message = "成果业务将在 PR09-10A 接入。只有用户明确确认的内容才会成为正式成果。",
                modifier = Modifier.testTag(ResourcesTestTags.EMPTY_STATE),
            )
        }
    }
}
