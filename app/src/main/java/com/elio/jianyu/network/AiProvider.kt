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
    GEMINI_31_FLASH_LITE(
        provider = AiProvider.GEMINI,
        modelId = "gemini-3.1-flash-lite",
        displayName = "Gemini 3.1 Flash Lite",
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
    ISSUE_EXECUTION("议题执行", "议题工作流中的文本执行", setOf(AiProvider.GEMINI, AiProvider.DEEPSEEK)),
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
        selectModel(useCase, defaultModel(provider))
    }

    fun selectModel(useCase: AiUseCase, model: AiModel) {
        require(model.provider in useCase.supportedProviders) { "该用途不支持 ${model.provider.displayName}" }
        save(_configuration.value.copyWith(useCase, model))
    }

    private fun loadConfiguration(): AiRuntimeConfiguration {
        return AiRuntimeConfiguration(
            AiUseCase.entries.associateWith { useCase ->
                preferences.getString("$KEY_MODEL.${useCase.name}", null)
                    ?.let { raw -> AiModel.entries.firstOrNull { it.name == raw } }
                    ?.takeIf { it.provider in useCase.supportedProviders }
                    ?: defaultModel(useCase.supportedProviders.first())
            },
        )
    }

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

fun defaultModel(provider: AiProvider): AiModel = when (provider) {
    AiProvider.GEMINI -> AiModel.GEMINI_35_FLASH
    AiProvider.DEEPSEEK -> AiModel.DEEPSEEK_V4_FLASH
}
