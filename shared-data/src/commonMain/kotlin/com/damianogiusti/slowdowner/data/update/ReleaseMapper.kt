package com.damianogiusti.slowdowner.data.update

import com.damianogiusti.slowdowner.domain.update.AppRelease
import com.damianogiusti.slowdowner.domain.update.AppVersion

object ReleaseMapper {

    /** Null for a tag that is not a version. [assetName]: the file this computer installs, if any. */
    fun toDomain(dto: ReleaseDto, assetName: String?): AppRelease? {
        val version = AppVersion.parse(dto.tagName) ?: return null
        return AppRelease(
            version = version,
            pageUrl = dto.htmlUrl,
            canInstall = assetFor(dto, assetName) != null,
        )
    }

    fun assetFor(dto: ReleaseDto, assetName: String?): ReleaseAssetDto? =
        assetName?.let { name -> dto.assets.firstOrNull { it.name == name } }
}
