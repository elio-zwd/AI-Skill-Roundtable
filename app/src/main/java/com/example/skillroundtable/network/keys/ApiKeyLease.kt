package com.example.skillroundtable.network.keys

import com.example.skillroundtable.network.ApiKeySource

/**
 * 封装在网络边界使用的 API Key 租约。
 * 外部只公开 keyId、displayName、source 等掩码或结构信息，真正的 secret 仅供网络层引擎读取。
 */
data class ApiKeyLease internal constructor(
    val keyId: String,
    val displayName: String,
    internal val secret: String,
    val source: ApiKeySource
)
