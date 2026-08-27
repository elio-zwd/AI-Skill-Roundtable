package com.elio.jianyu.network.keys

import com.elio.jianyu.network.ApiKeySource
import com.elio.jianyu.network.AiProvider

/**
 * 封装在网络边界使用的 API Key 租约。
 * 只公开 Key 标识、所属提供商和显示名。明文由对应提供商的请求执行器在网络边界按 keyId 解析。
 */
data class ApiKeyLease(
    val keyId: String,
    val displayName: String,
    val provider: AiProvider,
    val source: ApiKeySource
)
