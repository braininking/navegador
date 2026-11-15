package com.example.security

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArraySet

enum class BlockCategory {
    NONE,
    AD,
    TRACKER,
    MINER
}

object AdBlockEngine {

    private val adHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "ads.pubmatic.com",
        "adnxs.com",
        "openx.net",
        "rubiconproject.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "mgid.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "exoclick.com",
        "juicyads.com",
        "trafficjunky.net",
        "adsterra.com",
        "revcontent.com",
        "medianet.com",
        "media.net",
        "amazon-adsystem.com",
        "moatads.com",
        "adsafeprotected.com",
        "casalemedia.com",
        "bidswitch.net",
        "smartadserver.com",
        "yieldmo.com",
        "teads.tv",
        "spotxchange.com",
        "adcolony.com",
        "applovin.com",
        "unityads.unity3d.com",
        "vungle.com",
        "chartboost.com",
        "inmobi.com",
        "mopub.com",
        "adform.net",
        "33across.com",
        "triplelift.com",
        "sharethrough.com",
        "advertising.com",
        "quantserve.com"
    )

    private val trackerHosts = setOf(
        "google-analytics.com",
        "googletagmanager.com",
        "analytics.google.com",
        "connect.facebook.net",
        "pixel.facebook.com",
        "tr.snapchat.com",
        "analytics.tiktok.com",
        "ads-twitter.com",
        "analytics.twitter.com",
        "bat.bing.com",
        "clarity.ms",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "amplitude.com",
        "fullstory.com",
        "mouseflow.com",
        "crazyegg.com",
        "kissmetrics.com",
        "scorecardresearch.com",
        "imrworldwide.com",
        "newrelic.com",
        "nr-data.net",
        "sentry.io",
        "branch.io",
        "appsflyer.com",
        "adjust.com",
        "kochava.com",
        "omtrdc.net",
        "demdex.net",
        "krxd.net",
        "bluekai.com",
        "mathtag.com",
        "rlcdn.com"
    )

    private val minerHosts = setOf(
        "coinhive.com",
        "coin-hive.com",
        "authedmine.com",
        "cryptoloot.pro",
        "crypto-loot.com",
        "minero.cc",
        "webminepool.com",
        "jsecoin.com"
    )

    private val adPathFragments = listOf(
        "/pagead/",
        "/adserver",
        "/ad_banner",
        "/adsense/",
        "/prebid",
        "doubleclick.net",
        "googlesyndication",
        "/popunder",
        "/vast.xml",
        "/vpaid"
    )

    private val customBlockedDomains = CopyOnWriteArraySet<String>()
    private val allowlistedDomains = CopyOnWriteArraySet<String>()

    fun addCustomRule(domain: String) {
        val clean = domain.trim().lowercase().removePrefix("www.")
        if (clean.isNotBlank()) customBlockedDomains.add(clean)
    }

    fun allowSite(host: String) {
        val clean = host.trim().lowercase().removePrefix("www.")
        if (clean.isNotBlank()) allowlistedDomains.add(clean)
    }

    fun classifyRequest(
        url: String,
        pageHost: String?,
        adBlockEnabled: Boolean,
        strictTrackerBlock: Boolean
    ): BlockCategory {
        if (!adBlockEnabled) return BlockCategory.NONE
        val cleanPageHost = pageHost?.lowercase()?.removePrefix("www.")
        if (cleanPageHost != null && allowlistedDomains.contains(cleanPageHost)) {
            return BlockCategory.NONE
        }

        val host = try {
            java.net.URI(url).host?.lowercase()?.removePrefix("www.")
        } catch (_: Exception) {
            null
        } ?: return BlockCategory.NONE

        if (matchesDomainSet(host, minerHosts)) return BlockCategory.MINER
        if (matchesDomainSet(host, customBlockedDomains)) return BlockCategory.AD
        if (matchesDomainSet(host, adHosts)) return BlockCategory.AD
        if (strictTrackerBlock && matchesDomainSet(host, trackerHosts)) return BlockCategory.TRACKER

        val lowerUrl = url.lowercase()
        if (adPathFragments.any { lowerUrl.contains(it) }) {
            return BlockCategory.AD
        }

        return BlockCategory.NONE
    }

    private fun matchesDomainSet(host: String, domainSet: Set<String>): Boolean {
        if (domainSet.contains(host)) return true
        var current = host
        while (current.contains('.')) {
            current = current.substringAfter('.')
            if (domainSet.contains(current)) return true
        }
        return false
    }

    fun emptyBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    fun isVideoResourceUrl(url: String): Boolean {
        val lower = url.lowercase().substringBefore('?')
        return lower.endsWith(".mp4") ||
            lower.endsWith(".m3u8") ||
            lower.endsWith(".webm") ||
            lower.endsWith(".mov") ||
            lower.endsWith(".mkv") ||
            lower.contains("mime=video") ||
            lower.contains("/videoplayback")
    }
}
