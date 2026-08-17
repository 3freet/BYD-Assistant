package com.kangrio.byd.assistant.data

import com.google.gson.annotations.SerializedName

data class GitHubAsset(
    @SerializedName("name")
    val name: String,
    @SerializedName("browser_download_url")
    val browser_download_url: String,
    @SerializedName("size")
    val size: Long
)
