package com.kangrio.byd.assistant.data

data class GitHubRelease(
    val tag_name: String,
    val name: String?,
    val prerelease: Boolean,
    val html_url: String,
    val body: String? = null,
    val assets: List<GitHubAsset>
)
