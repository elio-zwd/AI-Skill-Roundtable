package com.elio.jianyu.audio.assets

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormalAudioStageBArchitectureTest {
    private val repositoryRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }

    @Test
    fun stageBAdaptersDoNotReconnectFormalAudioToLegacyMessageAudioChain() {
        val roots = listOf(
            "app/src/main/java/com/elio/jianyu/audio/work",
            "app/src/main/java/com/elio/jianyu/audio/runtime",
            "app/src/main/java/com/elio/jianyu/audio/playback",
            "app/src/main/java/com/elio/jianyu/network/audio",
            "app/src/main/java/com/elio/jianyu/ui/screens/execution",
            "app/src/main/java/com/elio/jianyu/data/AudioAssetRepositoryComponent.kt",
        ).map { File(repositoryRoot, it) }
        val sources = roots.flatMap { root ->
            when {
                root.isDirectory -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
                root.isFile -> listOf(root)
                else -> emptyList()
            }
        }
        assertTrue("阶段 B 正式音频适配源码不能为空", sources.isNotEmpty())

        val forbiddenTokens = listOf(
            "audioFilePath",
            "updateMessageAudio",
            "AudioTranscodeWorker",
            "generateTtsWav",
            "ChatDao",
        )
        val offenders = sources.flatMap { file ->
            val source = file.readText()
            forbiddenTokens.filter(source::contains).map { token ->
                "${file.relativeTo(repositoryRoot)} -> $token"
            }
        }

        assertTrue("阶段 B 重新接入旧 Message 音频链：$offenders", offenders.isEmpty())
    }

    @Test
    fun productionWorkerOnlyReadsStableAudioAssetIdFromInputData() {
        val worker = File(
            repositoryRoot,
            "app/src/main/java/com/elio/jianyu/audio/work/AudioAssetGenerationWorker.kt",
        ).readText()

        assertTrue(worker.contains("AudioGenerationWorkPolicy.AUDIO_ASSET_ID_KEY"))
        assertFalse(worker.contains("message_id"))
        assertFalse(worker.contains("source_text"))
        assertFalse(worker.contains("api_key"))
        assertFalse(worker.contains("wav_path"))
    }

    @Test
    fun runtimeRecoveryDoesNotEnqueueOrGenerateAudio() {
        val runtime = File(
            repositoryRoot,
            "app/src/main/java/com/elio/jianyu/audio/runtime/JianyuAudioRuntime.kt",
        ).readText()
        val route = File(
            repositoryRoot,
            "app/src/main/java/com/elio/jianyu/ui/screens/execution/AudioEnabledIssueExecutionRoute.kt",
        ).readText()

        assertFalse(runtime.contains("createGenerationRequest("))
        assertFalse(runtime.contains("schedule("))
        assertFalse(route.contains("createGenerationRequest("))
        assertFalse(route.contains("retryGeneration("))
        assertFalse(route.contains("enqueue"))
    }
}
