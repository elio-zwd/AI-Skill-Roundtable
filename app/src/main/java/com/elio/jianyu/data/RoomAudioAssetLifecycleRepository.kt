package com.elio.jianyu.data

import com.elio.jianyu.audio.assets.AudioAssetGlobalReferenceRepositoryPort
import com.elio.jianyu.audio.assets.AudioAssetLifecycleRepositoryPort
import com.elio.jianyu.audio.assets.AudioAssetRecord

/**
 * 为全局孤儿扫描提供 Room 中所有 AudioAsset 的只读引用视图。
 *
 * 生命周期写入继续委托给同一个 [RoomAudioAssetRepository]，这里只增加全局查询，
 * 不暴露 DAO，也不增加物理删除能力。查询失败必须向上传播，不能用空引用集合
 * 伪装成功并把整个受控目录误报为孤儿。
 */
class RoomAudioAssetLifecycleRepository(
    private val database: RoundtableDatabase,
    private val delegate: RoomAudioAssetRepository,
) : AudioAssetLifecycleRepositoryPort by delegate,
    AudioAssetGlobalReferenceRepositoryPort {

    override suspend fun listAllAudioAssets(): List<AudioAssetRecord> {
        val ids = database.openHelper.readableDatabase.query(
            "SELECT id FROM audio_assets ORDER BY createdAt, id",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        return ids.mapNotNull { delegate.loadAsset(it) }
    }
}
