package com.kangrio.byd.assistant.data

import com.google.gson.annotations.SerializedName

data class GitHubTreeResponse(
    @SerializedName("sha")
    val sha: String,
    @SerializedName("tree")
    val tree: List<GitHubTreeItem> = emptyList(),
    @SerializedName("truncated")
    val truncated: Boolean = false
)

data class GitHubTreeItem(
    @SerializedName("path")
    val path: String,
    @SerializedName("mode")
    val mode: String = "",
    @SerializedName("type")
    val type: String = "",
    @SerializedName("sha")
    val sha: String = "",
    @SerializedName("size")
    val size: Long = 0L,
    @SerializedName("url")
    val url: String = ""
)
