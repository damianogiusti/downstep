package com.damianogiusti.slowdowner.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseMapperTest {

    // Every installed Mac app asks for "Downstep-mac.dmg" by its exact name, 1.2.0 included: next to
    // the Intel DMG, it must still get the Apple silicon one, and only an Intel Mac gets the other.
    @Test
    fun `when a release has both Mac DMGs then each name gets its own file`() {
        val release = ReleaseDto(
            tagName = "v1.3.0",
            htmlUrl = "https://example.com/release",
            assets = listOf(
                ReleaseAssetDto("Downstep-mac-intel.dmg", "https://example.com/intel.dmg", 2, null),
                ReleaseAssetDto("Downstep-mac.dmg", "https://example.com/arm.dmg", 1, null),
                ReleaseAssetDto("Downstep-windows-x64.zip", "https://example.com/win.zip", 3, null),
            ),
        )

        assertEquals("https://example.com/arm.dmg", ReleaseMapper.assetFor(release, "Downstep-mac.dmg")?.downloadUrl)
        assertEquals("https://example.com/intel.dmg", ReleaseMapper.assetFor(release, "Downstep-mac-intel.dmg")?.downloadUrl)
    }
}
