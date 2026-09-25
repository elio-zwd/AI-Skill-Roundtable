package com.elio.jianyu.telemetry

data class CloudInteractionPolicyResult(
    val store: Boolean,
    val previousInteractionId: String?
)

object CloudInteractionRequestPolicy {
    fun apply(
        requestedStore: Boolean?,
        requestedPreviousInteractionId: String?
    ): CloudInteractionPolicyResult {
        val store = requestedStore == true
        return CloudInteractionPolicyResult(
            store = store,
            previousInteractionId = requestedPreviousInteractionId.takeIf { store }
        )
    }
}
