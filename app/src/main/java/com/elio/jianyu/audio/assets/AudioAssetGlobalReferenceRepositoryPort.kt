package com.elio.jianyu.audio.assets

/**
 * 孤儿扫描使用的全局只读引用视图。
 *
 * 只有实现该能力的正式 Repository 才允许对整个受控根目录给出孤儿结论，
 * 避免把其他议题仍在引用的文件误判为当前议题孤儿。
 */
interface AudioAssetGlobalReferenceRepositoryPort {
    suspend fun listAllAudioAssets(): List<AudioAssetRecord>
}
