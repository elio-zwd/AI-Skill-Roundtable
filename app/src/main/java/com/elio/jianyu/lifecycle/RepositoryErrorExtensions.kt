package com.elio.jianyu.lifecycle

import com.elio.jianyu.data.RepositoryError

/** 生命周期 UI 与 Worker 只暴露稳定状态码，不暴露异常或用户内容。 */
internal val RepositoryError.InvalidState.reason: String
    get() = stateCode
