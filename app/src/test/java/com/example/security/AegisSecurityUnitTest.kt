package com.example.security

import com.example.browser.ExtensionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AegisSecurityUnitTest {

    @Test
    fun adBlockEngine_blocksKnownAdAndTrackerDomains() {
        assertEquals(
            BlockCategory.AD,
            AdBlockEngine.classifyRequest(
                url = "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
                pageHost = "example.com",
                adBlockEnabled = true,
                strictTrackerBlock = true
            )
        )
        assertEquals(
            BlockCategory.TRACKER,
            AdBlockEngine.classifyRequest(
                url = "https://www.google-analytics.com/analytics.js",
                pageHost = "example.com",
                adBlockEnabled = true,
                strictTrackerBlock = true
            )
        )
        assertEquals(
            BlockCategory.NONE,
            AdBlockEngine.classifyRequest(
                url = "https://en.wikipedia.org/wiki/Internet_privacy",
                pageHost = "en.wikipedia.org",
                adBlockEnabled = true,
                strictTrackerBlock = true
            )
        )
    }

    @Test
    fun adBlockEngine_detectsVideoUrls() {
        assertTrue(AdBlockEngine.isVideoResourceUrl("https://cdn.example.com/stream/video.mp4?token=123"))
        assertTrue(AdBlockEngine.isVideoResourceUrl("https://live.example.com/hls/master.m3u8"))
        assertFalse(AdBlockEngine.isVideoResourceUrl("https://example.com/styles/main.css"))
    }

    @Test
    fun extensionCatalog_matchesWildcardPatternsAndBuildsScript() {
        assertTrue(ExtensionCatalog.matchesUrl("*://*/*", "https://developer.mozilla.org/en-US/"))
        assertTrue(ExtensionCatalog.matchesUrl("https://*.wikipedia.org/*", "https://en.wikipedia.org/wiki/Privacy"))
        assertFalse(ExtensionCatalog.matchesUrl("https://github.com/*", "https://duckduckgo.com"))

        val builtIns = ExtensionCatalog.defaultBuiltInExtensions("user_1")
        val script = ExtensionCatalog.buildCombinedInjectionScript(builtIns, "https://example.com")
        assertTrue(script.contains("uBlock") || script.contains("adsbygoogle"))
    }
}
