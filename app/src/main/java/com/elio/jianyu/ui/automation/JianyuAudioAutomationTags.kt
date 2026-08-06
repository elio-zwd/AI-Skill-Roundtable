package com.elio.jianyu.ui.automation

/** PR09-10B 独立音频资产 UI 的稳定自动化标签。 */
object JianyuAudioAutomationTags {
    const val ENTRY = "audio_assets_entry"
    const val DIALOG = "audio_assets_dialog"
    const val PANEL = "audio_assets_panel"
    const val REFRESH = "audio_assets_refresh"
    const val RECONCILE = "audio_assets_reconcile"
    const val CONFIRMATION_DIALOG = "audio_assets_confirmation_dialog"
    const val CONFIRM = "audio_assets_confirmation_confirm"
    const val DISMISS = "audio_assets_confirmation_cancel"

    fun messageGenerate(messageId: Long): String =
        "audio_message_generate_${JianyuAutomationTags.normalizedStableId(messageId.toString())}"

    fun artifactGenerate(artifactId: String): String =
        "audio_artifact_generate_${JianyuAutomationTags.normalizedStableId(artifactId)}"

    fun asset(audioAssetId: String): String =
        "audio_asset_${JianyuAutomationTags.normalizedStableId(audioAssetId)}"

    fun assetPlay(audioAssetId: String): String =
        "audio_asset_play_${JianyuAutomationTags.normalizedStableId(audioAssetId)}"

    fun assetRetry(audioAssetId: String): String =
        "audio_asset_retry_${JianyuAutomationTags.normalizedStableId(audioAssetId)}"

    fun assetCancel(audioAssetId: String): String =
        "audio_asset_cancel_${JianyuAutomationTags.normalizedStableId(audioAssetId)}"

    fun assetDelete(audioAssetId: String): String =
        "audio_asset_delete_${JianyuAutomationTags.normalizedStableId(audioAssetId)}"

    val frozenStaticTags: List<String> = listOf(
        ENTRY,
        DIALOG,
        PANEL,
        REFRESH,
        RECONCILE,
        CONFIRMATION_DIALOG,
        CONFIRM,
        DISMISS,
    )
}
