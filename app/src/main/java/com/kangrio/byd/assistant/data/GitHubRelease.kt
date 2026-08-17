package com.kangrio.byd.assistant.data

import com.google.gson.annotations.SerializedName

data class GitHubRelease(
    @SerializedName("tag_name")
    val tag_name: String,
    @SerializedName("name")
    val name: String?,
    @SerializedName("prerelease")
    val prerelease: Boolean,
    @SerializedName("html_url")
    val html_url: String,
    @SerializedName("body")
    val body: String? = null,
    @SerializedName("assets")
    val assets: List<GitHubAsset>
)
