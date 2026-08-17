package com.kangrio.byd.assistant.data

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val body: String,
    val htmlUrl: String,
    val downloadUrl: String,
    val apkName: String,
    val size: Long = 0L,
    val publishedAt: String = ""
)