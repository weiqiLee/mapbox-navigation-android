package com.mapbox.navigation.base.options

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URISyntaxException

class OnboardRouterOptionsTest {

    private val validFilePath =
        """/data/user/0/com.mapbox.navigation.examples/files/Offline/api.mapbox.com/2020_02_02-03_00_00/tiles"""

    @Test
    fun `filePath should build with defaults`() {
        val onboardRouterOptions = OnboardRouterOptions.Builder()
            .filePath(validFilePath)
            .build()

        assertNotNull(onboardRouterOptions.tilesUri)
        assertNotNull(onboardRouterOptions.tilesVersion)
        assertNotNull(onboardRouterOptions.filePath)
    }

    @Test
    fun `filePath should successfully build custom file path`() {
        val onboardRouterOptions = OnboardRouterOptions.Builder()
            .filePath(validFilePath)
            .build()

        assertNotNull(onboardRouterOptions)
    }

    @Test
    fun `filePath should fail to build when not specified`() {
        val onboardRouterOptions = try {
            OnboardRouterOptions.Builder().build()
        } catch (e: IllegalStateException) {
            null
        }

        assertNull(onboardRouterOptions)
    }

    @Test
    fun `tilesUri should fail to build fake uri`() {
        val onboardRouterOptions = try {
            OnboardRouterOptions.Builder()
                .filePath(validFilePath)
                .tilesUri("fake uri")
        } catch (e: URISyntaxException) {
            null
        }

        assertNull(onboardRouterOptions)
    }
}
