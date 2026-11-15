package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aegisDataStore: DataStore<Preferences> by preferencesDataStore(name = "aegisx_local_prefs")

data class LocalBrowserConfig(
    val onboardingCompleted: Boolean = false,
    val preferredLanguage: String = "pt",
    val preferredLanguageLabel: String = "Português",
    val autoTranslateEnabled: Boolean = true,
    val adBlockEnabled: Boolean = true,
    val strictTrackerBlock: Boolean = true,
    val vpnAutoConnect: Boolean = false,
    val vpnRegion: String = "ch",
    val desktopModeDefault: Boolean = false,
    val darkThemeEnabled: Boolean = true,
    val searchEngine: String = "DuckDuckGo",
    val adsBlockedTotal: Long = 0L,
    val trackersBlockedTotal: Long = 0L
)

class LocalPreferencesStore(private val context: Context) {

    val configFlow: Flow<LocalBrowserConfig> = context.aegisDataStore.data.map { prefs ->
        LocalBrowserConfig(
            onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
            preferredLanguage = prefs[KEY_PREFERRED_LANG] ?: "pt",
            preferredLanguageLabel = prefs[KEY_PREFERRED_LANG_LABEL] ?: "Português",
            autoTranslateEnabled = prefs[KEY_AUTO_TRANSLATE] ?: true,
            adBlockEnabled = prefs[KEY_ADBLOCK] ?: true,
            strictTrackerBlock = prefs[KEY_STRICT_TRACKER] ?: true,
            vpnAutoConnect = prefs[KEY_VPN_AUTO] ?: false,
            vpnRegion = prefs[KEY_VPN_REGION] ?: "ch",
            desktopModeDefault = prefs[KEY_DESKTOP_DEFAULT] ?: false,
            darkThemeEnabled = prefs[KEY_DARK_THEME] ?: true,
            searchEngine = prefs[KEY_SEARCH_ENGINE] ?: "DuckDuckGo",
            adsBlockedTotal = prefs[KEY_ADS_BLOCKED] ?: 0L,
            trackersBlockedTotal = prefs[KEY_TRACKERS_BLOCKED] ?: 0L
        )
    }

    suspend fun saveLanguageOnboarding(langCode: String, langLabel: String, autoTranslate: Boolean) {
        context.aegisDataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = true
            prefs[KEY_PREFERRED_LANG] = langCode
            prefs[KEY_PREFERRED_LANG_LABEL] = langLabel
            prefs[KEY_AUTO_TRANSLATE] = autoTranslate
        }
    }

    suspend fun updateFromCloudOrLocal(config: LocalBrowserConfig) {
        context.aegisDataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = config.onboardingCompleted
            prefs[KEY_PREFERRED_LANG] = config.preferredLanguage
            prefs[KEY_PREFERRED_LANG_LABEL] = config.preferredLanguageLabel
            prefs[KEY_AUTO_TRANSLATE] = config.autoTranslateEnabled
            prefs[KEY_ADBLOCK] = config.adBlockEnabled
            prefs[KEY_STRICT_TRACKER] = config.strictTrackerBlock
            prefs[KEY_VPN_AUTO] = config.vpnAutoConnect
            prefs[KEY_VPN_REGION] = config.vpnRegion
            prefs[KEY_DESKTOP_DEFAULT] = config.desktopModeDefault
            prefs[KEY_DARK_THEME] = config.darkThemeEnabled
            prefs[KEY_SEARCH_ENGINE] = config.searchEngine
            prefs[KEY_ADS_BLOCKED] = config.adsBlockedTotal
            prefs[KEY_TRACKERS_BLOCKED] = config.trackersBlockedTotal
        }
    }

    suspend fun incrementBlockedCounters(adsDelta: Long, trackersDelta: Long) {
        if (adsDelta <= 0 && trackersDelta <= 0) return
        context.aegisDataStore.edit { prefs ->
            prefs[KEY_ADS_BLOCKED] = (prefs[KEY_ADS_BLOCKED] ?: 0L) + adsDelta
            prefs[KEY_TRACKERS_BLOCKED] = (prefs[KEY_TRACKERS_BLOCKED] ?: 0L) + trackersDelta
        }
    }

    private companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_PREFERRED_LANG = stringPreferencesKey("preferred_lang")
        val KEY_PREFERRED_LANG_LABEL = stringPreferencesKey("preferred_lang_label")
        val KEY_AUTO_TRANSLATE = booleanPreferencesKey("auto_translate")
        val KEY_ADBLOCK = booleanPreferencesKey("adblock_enabled")
        val KEY_STRICT_TRACKER = booleanPreferencesKey("strict_tracker")
        val KEY_VPN_AUTO = booleanPreferencesKey("vpn_auto")
        val KEY_VPN_REGION = stringPreferencesKey("vpn_region")
        val KEY_DESKTOP_DEFAULT = booleanPreferencesKey("desktop_default")
        val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        val KEY_SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val KEY_ADS_BLOCKED = longPreferencesKey("ads_blocked_total")
        val KEY_TRACKERS_BLOCKED = longPreferencesKey("trackers_blocked_total")
    }
}
