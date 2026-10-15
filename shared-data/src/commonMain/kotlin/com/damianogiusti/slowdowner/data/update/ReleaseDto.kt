package com.damianogiusti.slowdowner.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The fields we use of a GitHub release. */
@Serializable
data class ReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<ReleaseAssetDto> = emptyList(),
)

@Serializable
data class ReleaseAssetDto(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long,
    /** "sha256:<hex>". GitHub computes it for every file uploaded since mid 2025. */
    val digest: String? = null,
)
