package com.elio.jianyu.audio.runtime

import android.content.Context
import com.elio.jianyu.audio.assets.AudioAssetLifecycleService
import com.elio.jianyu.audio.assets.AudioAssetPlaybackManager
import com.elio.jianyu.audio.assets.AudioFileStore
import com.elio.jianyu.audio.assets.AudioGenerationCoordinator
import com.elio.jianyu.audio.playback.AndroidAudioAssetPlayerFactory
import com.elio.jianyu.audio.work.WorkManagerAudioGenerationScheduler
import com.elio.jianyu.data.RoomAudioAssetLifecycleRepository
import com.elio.jianyu.data.RoomAudioAssetRepository
import com.elio.jianyu.data.RoundtableDatabase
import com.elio.jianyu.network.audio.ByokAudioGenerationGateway
import java.io.File
import java.util.UUID

/** 正式音频资产链在 App 组合层的共享运行时。 */
data class JianyuAudioRuntime(
    val generationCoordinator: AudioGenerationCoordinator,
    val lifecycleService: AudioAssetLifecycleService,
    val playbackManager: AudioAssetPlaybackManager,
    val fileStore: AudioFileStore,
)

fun createJianyuAudioRuntime(
    context: Context,
    database: RoundtableDatabase,
): JianyuAudioRuntime {
    val repository = RoomAudioAssetRepository(database)
    val lifecycleRepository = RoomAudioAssetLifecycleRepository(database, repository)
    val fileStore = AudioFileStore(File(context.filesDir, "jianyu-audio"))
    val scheduler = WorkManagerAudioGenerationScheduler(context)
    val generationCoordinator = AudioGenerationCoordinator(
        repository = repository,
        scheduler = scheduler,
        gateway = ByokAudioGenerationGateway(context),
        fileStore = fileStore,
        audioAssetIdFactory = { UUID.randomUUID().toString() },
    )
    return JianyuAudioRuntime(
        generationCoordinator = generationCoordinator,
        lifecycleService = AudioAssetLifecycleService(
            repository = lifecycleRepository,
            scheduler = scheduler,
            fileStore = fileStore,
        ),
        playbackManager = AudioAssetPlaybackManager(
            fileStore = fileStore,
            playerFactory = AndroidAudioAssetPlayerFactory(),
        ),
        fileStore = fileStore,
    )
}
