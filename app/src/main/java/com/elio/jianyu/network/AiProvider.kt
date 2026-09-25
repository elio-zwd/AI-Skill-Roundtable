package com.elio.jianyu.network

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 当前工作台支持的模型提供商。 */
enum class AiProvider(
    val displayName: String,
    internal val storageId: String,
) {
    GEMINI("Gemini", "gemini"),
    DEEPSEEK("DeepSeek", "deepseek"),
}

/** 用户可选择的文本模型。模型 ID 直接传给对应提供商。 */
enum class AiModel(
    val provider: AiProvider,
    val modelId: String,
    val displayName: String,
    val supportsWebGrounding: Boolean = false,
) {
    GEMINI_38_FLASH(
        provider = AiProvider.GEMINI,
        modelId = "gemini-3.8-flash",
        displayName = "Gemini 3.8 Flash",
        supportsWebGrounding = true,
    ),
    GEMINI_37_FLASH(
        provider = AiProvider.GEMINI,
        modelId = "gemini-3.7-flash",
        displayName = "Gemini 3.7 Flash",
        supportsWebGrounding = true,
    ),
    GEMINI_36_FLASH(
        provider = AiProvider.GEMINI,
        modelId = "gemini-3.6-flash",
        displayName = "Gemini 3.6 Flash",
        supportsWebGrounding = true,
    ),
    GEMINI_35_FLASH(
        provider = AiProvider.GEMINI,
        modelId = "gemini-3.5-flash",
        displayName = "Gemini 3.5 Flash",
        supportsWebGrounding = true,
    ),
    GEMINI_35_FLASH_LITE(
        provider = AiProvider.GEMINI,
        modelId = "gemini-3.5-flash-lite",
        displayName = "Gemini 3.5 Flash Lite",
        supportsWebGrounding = true,
    ),
    GEMINI_31_FLASH_LITE(
        provider = AiProvider.GEMINI,
        modelId = "gemini-3.1-flash-lite",
        displayName = "Gemini 3.1 Flash Lite",
        supportsWebGrounding = true,
    ),
    GEMINI_25_FLASH(
        provider = AiProvider.GEMINI,
        modelId = "gemini-2.5-flash",
        displayName = "Gemini 2.5 Flash",
        supportsWebGrounding = true,
    ),
    GEMINI_25_FLASH_LITE(
        provider = AiProvider.GEMINI,
        modelId = "gemini-2.5-flash-lite",
        displayName = "Gemini 2.5 Flash Lite",
        supportsWebGrounding = true,
    ),
    DEEPSEEK_V4_FLASH(
        provider = AiProvider.DEEPSEEK,
        modelId = "deepseek-v4-flash",
        displayName = "DeepSeek V4 Flash",
    ),
    DEEPSEEK_V4_PRO(
        provider = AiProvider.DEEPSEEK,
        modelId = "deepseek-v4-pro",
        displayName = "DeepSeek V4 Pro",
    ),
}

data class AiRuntimeConfiguration(
    private val models: Map<AiUseCase, AiModel>,
) {
    fun modelFor(useCase: AiUseCase): AiModel = models.getValue(useCase)
}

/** 每一种文本调用用途都有独立模型选择，避免用全局开关隐式改变其他功能。 */
enum class AiUseCase(
    val displayName: String,
    val description: String,
    val supportedProviders: Set<AiProvider>,
) {
    SESSION_TITLE("对话标题", "首次提问后的会话标题提炼", setOf(AiProvider.GEMINI, AiProvider.DEEPSEEK)),
    MATERIAL_BROKER("资料决策", "选择本地参考资料与检索需求", setOf(AiProvider.GEMINI, AiProvider.DEEPSEEK)),
    WEB_GROUNDING("联网检索", "调用 Google Search 获取实时信息", setOf(AiProvider.GEMINI)),
    ROUNDTABLE_ANSWER("对话角色回答", "Skill 角色的最终文本回答", setOf(AiProvider.GEMINI, AiProvider.DEEPSEEK)),
    ISSUE_EXECUTION("成果生成", "生成并整理可保存的成果内容", setOf(AiProvider.GEMINI, AiProvider.DEEPSEEK)),
}

/** 只持久化用户明确选择的提供商与模型，不保存任何密钥。 */
class AiConfigurationRepository(context: Context) {
    private companion object {
        const val PREFS_NAME = "ai_runtime_configuration"
        const val KEY_PROVIDER = "provider"
        const val KEY_MODEL = "model"
    }

    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _configuration = MutableStateFlow(loadConfiguration())
    val configuration: StateFlow<AiRuntimeConfiguration> = _configuration.asStateFlow()

    fun selectProvider(useCase: AiUseCase, provider: AiProvider) {
        require(provider in useCase.supportedProviders) { "该用途不支持 ${provider.displayName}" }
        selectModel(useCase, defaultModel(useCase, provider))
    }

    fun selectModel(useCase: AiUseCase, model: AiModel) {
        require(model.provider in useCase.supportedProviders) { "该用途不支持 ${model.provider.displayName}" }
        save(_configuration.value.copyWith(useCase, model))
    }

    fun reset(): Boolean {
        val committed = preferences.edit().clear().commit()
        if (committed) {
            _configuration.value = defaultConfiguration()
        }
        return committed
    }

    private fun loadConfiguration(): AiRuntimeConfiguration {
        return AiRuntimeConfiguration(
            AiUseCase.entries.associateWith { useCase ->
                preferences.getString("$KEY_MODEL.${useCase.name}", null)
                    ?.let { raw -> AiModel.entries.firstOrNull { it.name == raw } }
                    ?.takeIf { it.provider in useCase.supportedProviders }
                    ?: defaultModel(useCase)
            },
        )
    }

    private fun defaultConfiguration(): AiRuntimeConfiguration = AiRuntimeConfiguration(
        AiUseCase.entries.associateWith { useCase ->
            defaultModel(useCase)
        },
    )

    private fun save(configuration: AiRuntimeConfiguration) {
        val editor = preferences.edit()
        AiUseCase.entries.forEach { useCase ->
            editor.putString("$KEY_MODEL.${useCase.name}", configuration.modelFor(useCase).name)
        }
        editor.remove(KEY_PROVIDER).apply()
        _configuration.value = configuration
    }
}

private fun AiRuntimeConfiguration.copyWith(
    useCase: AiUseCase,
    model: AiModel,
): AiRuntimeConfiguration = AiRuntimeConfiguration(
    AiUseCase.entries.associateWith { entry -> if (entry == useCase) model else modelFor(entry) },
)

private val GEMINI_MODELS_WITHOUT_MINIMAL_THINKING = setOf(
    AiModel.GEMINI_38_FLASH,
    AiModel.GEMINI_37_FLASH,
    AiModel.GEMINI_25_FLASH,
    AiModel.GEMINI_25_FLASH_LITE,
)

internal fun AiModel.geminiInteractionThinkingLevel(requestedLevel: String): String {
    require(provider == AiProvider.GEMINI) { "只有 Gemini 模型可以使用 Interactions thinking_level" }
    require(requestedLevel in setOf("minimal", "low", "medium", "high")) {
        "Gemini 不支持思考档位：$requestedLevel"
    }
    return if (requestedLevel == "minimal" && this in GEMINI_MODELS_WITHOUT_MINIMAL_THINKING) {
        "low"
    } else {
        requestedLevel
    }
}

fun defaultModel(provider: AiProvider): AiModel = when (provider) {
    AiProvider.GEMINI -> AiModel.GEMINI_38_FLASH
    AiProvider.DEEPSEEK -> AiModel.DEEPSEEK_V4_FLASH
}

fun defaultModel(useCase: AiUseCase): AiModel =
    defaultModel(useCase, useCase.supportedProviders.first())

private fun defaultModel(
    useCase: AiUseCase,
    provider: AiProvider,
): AiModel {
    require(provider in useCase.supportedProviders) { "该用途不支持 ${provider.displayName}" }
    return if (useCase == AiUseCase.WEB_GROUNDING && provider == AiProvider.GEMINI) {
        AiModel.GEMINI_25_FLASH
    } else {
        defaultModel(provider)
    }
}
