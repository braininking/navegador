package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserPreferences(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val preferredLanguage: String = "pt",
    val preferredLanguageLabel: String = "Português",
    val autoTranslateEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val adBlockEnabled: Boolean = true,
    val strictTrackerBlock: Boolean = true,
    val vpnAutoConnect: Boolean = false,
    val vpnRegion: String = "Zurich, CH",
    val desktopModeDefault: Boolean = false,
    val darkThemeEnabled: Boolean = true,
    val searchEngine: String = "DuckDuckGo",
    val deviceName: String = "Android Device",
    val adsBlockedTotal: Long = 0L,
    val trackersBlockedTotal: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncedBookmark(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val url: String = "",
    val folder: String = "Favoritos",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BrowserExtension(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val version: String = "1.0.0",
    val author: String = "Comunidade",
    val category: String = "custom",
    val matchPattern: String = "*://*/*",
    val jsCode: String = "",
    val cssCode: String = "",
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val runAt: String = "document_end",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncedTab(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val url: String = "",
    val deviceName: String = "Android",
    val isIncognito: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
