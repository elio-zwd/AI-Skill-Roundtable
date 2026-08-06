package com.elio.jianyu.audio.assets

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAssetArchitectureTest {
    private val repositoryRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }

    private val formalAudioRoot: File by lazy {
        File(repositoryRoot, "app/src/main/java/com/elio/jianyu/audio/assets")
    }

    @Test
    fun formalAudioAssetsDoNotDependOnLegacyChatPersistenceOrNetworkClients() {
        assertTrue("正式音频资产目录必须存在", formalAudioRoot.isDirectory)
        val productionSources = formalAudioRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue("正式音频资产目录不能是空目录", productionSources.isNotEmpty())

        val forbiddenTokens = listOf(
            "ChatDao",
            "RoundtableDatabase",
            "AudioTranscodeWorker",
            "updateMessageAudio",
            "audioFilePath",
            "LiveApiClient",
            "retrofit2.",
            "okhttp3.",
            "System.getenv",
            "System.getProperty(\"API",
            "dotenv",
            "com.elio.jianyu.data.ChatSession",
        )
        val offenders = productionSources.flatMap { file ->
            val source = file.readText()
            forbiddenTokens.filter(source::contains).map { token ->
                "${file.relativeTo(repositoryRoot)} -> $token"
            }
        }

        assertTrue("正式音频资产链发现旧链或生产网络耦合：$offenders", offenders.isEmpty())
    }

    @Test
    fun workerPlanExposesOnlyStableInternalAudioAssetId() {
        val source = File(formalAudioRoot, "AudioGenerationWorkPolicy.kt").readText()

        assertTrue(source.contains("const val AUDIO_ASSET_ID_KEY: String = \"audio_asset_id\""))
        assertTrue(source.contains("inputData = mapOf(AUDIO_ASSET_ID_KEY to audioAssetId)"))
        assertFalse(source.contains("message_id"))
        assertFalse(source.contains("wav_path"))
        assertFalse(source.contains("api_key"))
        assertFalse(source.contains("source_text"))
    }

    @Test
    fun formalAudioPackageHasNoRoomOrWorkManagerImportsDuringParallelStageA() {
        val productionSources = formalAudioRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val imports = productionSources.flatMap { file ->
            file.readLines().filter { line -> line.startsWith("import ") }
        }

        assertFalse(imports.any { it.startsWith("import androidx.room") })
        assertFalse(imports.any { it.startsWith("import androidx.work") })
        assertFalse(imports.any { it.startsWith("import com.elio.jianyu.network") })
        assertEquals(
            emptyList<String>(),
            imports.filter { it.contains("JianyuRepository") || it.contains("ResourceLifecycleDao") },
        )
    }
}
