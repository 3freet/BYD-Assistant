package com.kangrio.byd.assistant.data

data class OnlineWakeWordModel(
    val name: String,
    val fileName: String,
    val path: String,
    val downloadUrl: String,
    val size: Long,
    val isInstalled: Boolean = false
)
