package com.elio.jianyu.network

import android.content.Context
import com.elio.jianyu.network.retry.ApiCallFailure
import com.elio.jianyu.telemetry.CloudInteractionSettings
import com.elio.jianyu.telemetry.TelemetryRepository

/** AI 模块组合入口：统一暴露提供商配置、Key 仓库与请求执行器。 */
object AiManager {
    @Volatile
    private var initialized = false

    private lateinit var configurationRepository: AiConfigurationRepository
    private lateinit var geminiKeys: ProviderKeyRepository
    private lateinit var deepSeekKeys: ProviderKeyRepository
    private lateinit var geminiExecutor: AiRequestExecutor
    private lateinit var deepSeekExecutor: AiRequestExecutor

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        TelemetryRepository.init(appContext)
        CloudInteractionSettings.init(appContext)
        configurationRepository = AiConfigurationRepository(appContext)
        geminiKeys = ProviderKeyRepository(appContext, AiProvider.GEMINI)
        deepSeekKeys = ProviderKeyRepository(appContext, AiProvider.DEEPSEEK)
        geminiExecutor = AiRequestExecutor(
            AiProvider.GEMINI,
            ProviderKeyAttemptReporterAdapter(geminiKeys),
        )
        deepSeekExecutor = AiRequestExecutor(
            AiProvider.DEEPSEEK,
            ProviderKeyAttemptReporterAdapter(deepSeekKeys),
        )
        initialized = true
    }

    fun configuration(context: Context): AiConfigurationRepository {
        initialize(context)
        return configurationRepository
    }

    fun keys(context: Context, provider: AiProvider): ProviderKeyRepository {
        initialize(context)
        return when (provider) {
            AiProvider.GEMINI -> geminiKeys
            AiProvider.DEEPSEEK -> deepSeekKeys
        }
    }

    fun keysForUseCase(context: Context, useCase: AiUseCase): ProviderKeyRepository {
        val model = configuration(context).configuration.value.modelFor(useCase)
        return keys(context, model.provider)
    }

    internal fun requests(context: Context, provider: AiProvider): AiRequestExecutor {
        initialize(context)
        return when (provider) {
            AiProvider.GEMINI -> geminiExecutor
            AiProvider.DEEPSEEK -> deepSeekExecutor
        }
    }

    suspend fun validateKey(context: Context, provider: AiProvider, keyId: String): ApiKeyValidationState {
        val repository = keys(context, provider)
        return repository.validateKey(keyId, validatorFor(provider))
    }

    suspend fun validateKeys(context: Context, provider: AiProvider, keyIds: List<String>) {
        keys(context, provider).validateKeys(keyIds, validatorFor(provider))
    }

    fun findKeyIdOrNull(secret: String): String? {
        if (!initialized) return null
        return listOf(geminiKeys, deepSeekKeys)
            .firstNotNullOfOrNull { repository -> repository.findKeyIdOrNull(secret) }
    }

    private fun validatorFor(provider: AiProvider): suspend (String) -> ApiKeyValidationState = when (provider) {
        AiProvider.GEMINI -> GeminiRestTransport::validateKey
        AiProvider.DEEPSEEK -> DeepSeekTransport::validateKey
    }

    private class ProviderKeyAttemptReporterAdapter(
        private val repository: ProviderKeyRepository,
    ) : ProviderKeyAttemptReporter {
        override fun secretFor(keyId: String): String? = repository.secretFor(keyId)

        override fun recordSuccess(sessionId: Long, keyId: String) {
            repository.recordSuccess(sessionId, keyId)
        }

        override fun recordFailure(keyId: String, failure: ApiCallFailure) {
            repository.recordFailure(keyId, failure)
        }
    }
}
