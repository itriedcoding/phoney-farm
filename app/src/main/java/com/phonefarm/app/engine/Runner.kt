package com.phonefarm.app.engine

data class RunResult(
    val ok: Boolean,
    val status: Int,
    val bytes: Long,
    val error: String?,
)
