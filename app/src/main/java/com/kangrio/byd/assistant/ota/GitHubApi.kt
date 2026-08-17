package com.kangrio.byd.assistant.ota

import com.kangrio.byd.assistant.data.GitHubRelease
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getAllReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitHubRelease>
}
