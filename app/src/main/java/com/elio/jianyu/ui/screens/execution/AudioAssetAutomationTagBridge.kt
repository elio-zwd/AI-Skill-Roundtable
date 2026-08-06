package com.elio.jianyu.ui.screens.execution

import com.elio.jianyu.ui.automation.JianyuAudioAutomationTags
import com.elio.jianyu.ui.automation.JianyuAutomationTags

/** 保持主标签对象访问形式，同时将 PR09-10B 标签集中在独立契约文件。 */
internal val JianyuAutomationTags.AudioAssets: JianyuAudioAutomationTags
    get() = JianyuAudioAutomationTags
