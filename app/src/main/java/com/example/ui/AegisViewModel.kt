package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.browser.DetectedVideoMedia
import com.example.browser.DownloadProgressState
import com.example.browser.ExtensionCatalog
import com.example.browser.VideoDownloader
import com.example.data.local.AegisDao
import com.example.data.local.DownloadedVideoEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.LocalBookmarkEntity
import com.example.data.local.LocalBrowserConfig
import com.example.data.local.LocalExtensionEntity
import com.example.data.local.LocalPreferencesStore
import com.example.data.local.OfflinePageEntity
import com.example.data.remote.AegisSyncRepository
import com.example.data.remote.BrowserExtension
import com.example.data.remote.GeneratedExtensionDraft
import com.example.data.remote.SyncedBookmark
import com.example.data.remote.SyncedTab
import com.example.data.remote.TranslationAiRepository
import com.example.data.remote.UserPreferences
import com.example.security.AegisVpnService
import com.example.security.VaultCryptoManager
import com.example.security.VpnStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.util.UUID

data class BrowserTabState(
    val id: String = "tab_${UUID.randomUUID().toString().take(8)}",
    val title: String = "Nova Aba Privada",
    val url: String = HOME_URL,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isIncognito: Boolean = false,
    val detectedSourceLang: String? = null,
    val isTranslated: Boolean = false,
    val isTranslating: Boolean = false,
    val translationFromCache: Boolean = false,
    val adsBlockedOnPage: Int = 0,
    val trackersBlockedOnPage: Int = 0,
    val detectedVideos: List<DetectedVideoMedia> = emptyList(),
    val pageError: String? = null,
    val lastExtractedText: String = "",
    val lastExtractedHtml: String = ""
) {
    companion object {
        const val HOME_URL = "about:aegisx"
    }
}

sealed interface WebViewCommand {
    data class LoadUrl(val tabId: String, val url: String) : WebViewCommand
    data class Reload(val tabId: String) : WebViewCommand
    data class GoBack(val tabId: String) : WebViewCommand
    data class GoForward(val tabId: String) : WebViewCommand
    data class ApplyTranslation(val tabId: String, val translationsJsonArray: String) : WebViewCommand
    data class RestoreOriginal(val tabId: String) : WebViewCommand
    data class InjectExtensions(val tabId: String, val script: String) : WebViewCommand
    data class TriggerSaveOfflineCapture(val tabId: String) : WebViewCommand
}

data class AegisUiState(
    val tabs: List<BrowserTabState> = listOf(BrowserTabState()),
    val activeTabIndex: Int = 0,
    val activeVideoPlayerMedia: DetectedVideoMedia? = null,
    val aiSummaryResult: String? = null,
    val isGeneratingSummary: Boolean = false,
    val isGeneratingExtension: Boolean = false,
    val generatedExtensionDraft: GeneratedExtensionDraft? = null,
    val quickTranslateOutput: String = "",
    val isQuickTranslating: Boolean = false,
    val isExtractingBlockedVideo: Boolean = false,
    val syncError: String? = null,
    val toastMessage: String? = null
) {
    val activeTab: BrowserTabState
        get() = tabs.getOrElse(activeTabIndex) { tabs.first() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AegisViewModel(
    private val syncRepository: AegisSyncRepository,
    private val translationRepository: TranslationAiRepository,
    private val localPrefs: LocalPreferencesStore,
    private val dao: AegisDao,
    private val videoDownloader: VideoDownloader,
    private val auth: FirebaseAuth = Firebase.auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AegisUiState())
    val uiState: StateFlow<AegisUiState> = _uiState.asStateFlow()

    private val _commands = MutableSharedFlow<WebViewCommand>(extraBufferCapacity = 24)
    val commands: SharedFlow<WebViewCommand> = _commands.asSharedFlow()

    val currentUser: StateFlow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, auth.currentUser)

    val localConfig: StateFlow<LocalBrowserConfig> = localPrefs.configFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalBrowserConfig())

    val vpnStatus: StateFlow<VpnStatus> = AegisVpnService.vpnStatus

    val downloadProgress: StateFlow<DownloadProgressState> = videoDownloader.progress

    val syncedPreferences: StateFlow<UserPreferences?> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(null)
            } else {
                syncRepository.observePreferences().catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing preferences: ${firestoreError?.message ?: error.message}")
                    emit(null)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val bookmarks: StateFlow<List<SyncedBookmark>> = dao.observeLocalBookmarks()
        .map { list ->
            val uid = auth.currentUser?.uid.orEmpty()
            list.map { it.toDomain(uid) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val extensions: StateFlow<List<BrowserExtension>> = dao.observeLocalExtensions()
        .map { list ->
            val uid = auth.currentUser?.uid.orEmpty()
            list.map { it.toDomain(uid) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            ExtensionCatalog.defaultBuiltInExtensions("")
        )

    val syncedTabs: StateFlow<List<SyncedTab>> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                syncRepository.observeSyncedTabs().catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val offlinePages: StateFlow<List<OfflinePageEntity>> = dao.observeOfflinePages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val downloadedVideos: StateFlow<List<DownloadedVideoEntity>> = dao.observeDownloadedVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val historyEntries: StateFlow<List<HistoryEntity>> = dao.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val translationCacheCount: StateFlow<Int> = dao.observeTranslationCacheCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    private val translatedUrlCache = mutableSetOf<String>()

    init {
        seedLocalBuiltInExtensions()
        observeOptionalCloudSync()
    }

    private fun seedLocalBuiltInExtensions() {
        viewModelScope.launch(Dispatchers.IO) {
            if (dao.getLocalExtensionsOnce().isEmpty()) {
                val builtIns = ExtensionCatalog.defaultBuiltInExtensions("").map { it.toEntity() }
                dao.upsertLocalExtensions(builtIns)
            }
        }
    }

    private fun observeOptionalCloudSync() {
        // Mirror cloud bookmarks & extensions into local Room whenever user is signed in
        viewModelScope.launch {
            currentUser.collectLatest { user ->
                if (user != null) {
                    bootstrapAccountSync(user)
                }
            }
        }
        viewModelScope.launch {
            currentUser.flatMapLatest { user ->
                if (user == null) flowOf(emptyList())
                else syncRepository.observeExtensions().catch { emit(emptyList()) }
            }.collect { cloudExtensions ->
                if (cloudExtensions.isNotEmpty()) {
                    dao.upsertLocalExtensions(cloudExtensions.map { it.toEntity() })
                }
            }
        }
        viewModelScope.launch {
            currentUser.flatMapLatest { user ->
                if (user == null) flowOf(emptyList())
                else syncRepository.observeBookmarks().catch { emit(emptyList()) }
            }.collect { cloudBookmarks ->
                if (cloudBookmarks.isNotEmpty()) {
                    dao.upsertLocalBookmarks(cloudBookmarks.map { it.toEntity() })
                }
            }
        }
    }

    private suspend fun bootstrapAccountSync(user: FirebaseUser) {
        try {
            val cloudPrefs = syncRepository.getPreferencesOnce()
            val currentLocal = localPrefs.configFlow.first()
            if (cloudPrefs != null) {
                // Only overwrite onboarding if local hasn't already chosen a language, or sync cloud settings
                val merged = LocalBrowserConfig(
                    onboardingCompleted = currentLocal.onboardingCompleted || cloudPrefs.onboardingCompleted,
                    preferredLanguage = if (currentLocal.onboardingCompleted) currentLocal.preferredLanguage else cloudPrefs.preferredLanguage,
                    preferredLanguageLabel = if (currentLocal.onboardingCompleted) currentLocal.preferredLanguageLabel else cloudPrefs.preferredLanguageLabel,
                    autoTranslateEnabled = cloudPrefs.autoTranslateEnabled,
                    adBlockEnabled = cloudPrefs.adBlockEnabled,
                    strictTrackerBlock = cloudPrefs.strictTrackerBlock,
                    vpnAutoConnect = cloudPrefs.vpnAutoConnect,
                    vpnRegion = cloudPrefs.vpnRegion,
                    desktopModeDefault = cloudPrefs.desktopModeDefault,
                    darkThemeEnabled = cloudPrefs.darkThemeEnabled,
                    searchEngine = cloudPrefs.searchEngine,
                    adsBlockedTotal = maxOf(currentLocal.adsBlockedTotal, cloudPrefs.adsBlockedTotal),
                    trackersBlockedTotal = maxOf(currentLocal.trackersBlockedTotal, cloudPrefs.trackersBlockedTotal)
                )
                localPrefs.updateFromCloudOrLocal(merged)
            } else {
                syncCurrentConfigToCloud(currentLocal)
            }

            // Push any local extensions / bookmarks created before signing in
            dao.getLocalExtensionsOnce().forEach { localExt ->
                runCatching { syncRepository.saveExtension(localExt.toDomain(user.uid)) }
            }
            dao.getLocalBookmarksOnce().forEach { localBm ->
                runCatching { syncRepository.addBookmark(localBm.title, localBm.url, localBm.folder, localBm.id) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Optional cloud sync skipped (offline or unavailable): ${e.message}")
        }
    }

    fun completeLanguageOnboarding(langCode: String, langLabel: String, autoTranslate: Boolean) {
        viewModelScope.launch {
            localPrefs.saveLanguageOnboarding(langCode, langLabel, autoTranslate)
            syncCurrentConfigToCloud(
                localConfig.value.copy(
                    onboardingCompleted = true,
                    preferredLanguage = langCode,
                    preferredLanguageLabel = langLabel,
                    autoTranslateEnabled = autoTranslate
                )
            )
            showToast("Idioma configurado: $langLabel")
        }
    }

    fun updateLanguagePreferences(langCode: String, langLabel: String, autoTranslate: Boolean) {
        viewModelScope.launch {
            val updated = localConfig.value.copy(
                onboardingCompleted = true,
                preferredLanguage = langCode,
                preferredLanguageLabel = langLabel,
                autoTranslateEnabled = autoTranslate
            )
            localPrefs.updateFromCloudOrLocal(updated)
            syncCurrentConfigToCloud(updated)
            translatedUrlCache.clear()
            showToast("Preferência salva: $langLabel")
        }
    }

    fun updateSecuritySettings(
        adBlockEnabled: Boolean = localConfig.value.adBlockEnabled,
        strictTrackerBlock: Boolean = localConfig.value.strictTrackerBlock,
        vpnAutoConnect: Boolean = localConfig.value.vpnAutoConnect,
        vpnRegion: String = localConfig.value.vpnRegion,
        desktopModeDefault: Boolean = localConfig.value.desktopModeDefault,
        darkThemeEnabled: Boolean = localConfig.value.darkThemeEnabled,
        searchEngine: String = localConfig.value.searchEngine
    ) {
        viewModelScope.launch {
            val updated = localConfig.value.copy(
                adBlockEnabled = adBlockEnabled,
                strictTrackerBlock = strictTrackerBlock,
                vpnAutoConnect = vpnAutoConnect,
                vpnRegion = vpnRegion,
                desktopModeDefault = desktopModeDefault,
                darkThemeEnabled = darkThemeEnabled,
                searchEngine = searchEngine
            )
            localPrefs.updateFromCloudOrLocal(updated)
            syncCurrentConfigToCloud(updated)
        }
    }

    fun forceCloudSync() {
        val user = auth.currentUser
        if (user == null) {
            showToast("Conecte sua conta Google nesta seção para sincronizar entre dispositivos.")
            return
        }
        viewModelScope.launch {
            bootstrapAccountSync(user)
            syncCurrentConfigToCloud(localConfig.value)
            showToast("Perfil e preferências sincronizados na nuvem.")
        }
    }

    private suspend fun syncCurrentConfigToCloud(cfg: LocalBrowserConfig) {
        val user = auth.currentUser ?: return
        try {
            syncRepository.saveOrUpdatePreferences(
                UserPreferences(
                    userId = user.uid,
                    displayName = user.displayName,
                    email = user.email,
                    preferredLanguage = cfg.preferredLanguage,
                    preferredLanguageLabel = cfg.preferredLanguageLabel,
                    autoTranslateEnabled = cfg.autoTranslateEnabled,
                    onboardingCompleted = cfg.onboardingCompleted,
                    adBlockEnabled = cfg.adBlockEnabled,
                    strictTrackerBlock = cfg.strictTrackerBlock,
                    vpnAutoConnect = cfg.vpnAutoConnect,
                    vpnRegion = cfg.vpnRegion,
                    desktopModeDefault = cfg.desktopModeDefault,
                    darkThemeEnabled = cfg.darkThemeEnabled,
                    searchEngine = cfg.searchEngine,
                    adsBlockedTotal = cfg.adsBlockedTotal,
                    trackersBlockedTotal = cfg.trackersBlockedTotal
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cloud sync deferred (offline): ${e.message}")
        }
    }

    // --- Browser Navigation & Tabs ---

    fun navigateToInput(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        val resolvedUrl = resolveUrlOrSearch(trimmed, localConfig.value.searchEngine)
        val tab = _uiState.value.activeTab
        translatedUrlCache.remove(tab.id)
        updateTab(tab.id) {
            it.copy(
                url = resolvedUrl,
                isLoading = true,
                pageError = null,
                isTranslated = false,
                detectedSourceLang = null,
                detectedVideos = emptyList(),
                adsBlockedOnPage = 0,
                trackersBlockedOnPage = 0
            )
        }
        _commands.tryEmit(WebViewCommand.LoadUrl(tab.id, resolvedUrl))
    }

    fun openUrlInBrowser(url: String, title: String? = null) {
        val tab = _uiState.value.activeTab
        translatedUrlCache.remove(tab.id)
        updateTab(tab.id) {
            it.copy(
                url = url,
                title = title ?: url,
                isLoading = true,
                pageError = null,
                isTranslated = false,
                detectedVideos = emptyList()
            )
        }
        _commands.tryEmit(WebViewCommand.LoadUrl(tab.id, url))
    }

    fun goHome() {
        val tab = _uiState.value.activeTab
        updateTab(tab.id) {
            it.copy(
                url = BrowserTabState.HOME_URL,
                title = "Nova Aba Privada",
                isLoading = false,
                pageError = null,
                isTranslated = false,
                detectedVideos = emptyList()
            )
        }
    }

    fun reloadActiveTab() {
        val tab = _uiState.value.activeTab
        if (tab.url == BrowserTabState.HOME_URL) return
        translatedUrlCache.remove(tab.id)
        updateTab(tab.id) { it.copy(pageError = null, isTranslated = false) }
        _commands.tryEmit(WebViewCommand.Reload(tab.id))
    }

    fun goBackActiveTab() {
        _commands.tryEmit(WebViewCommand.GoBack(_uiState.value.activeTab.id))
    }

    fun goForwardActiveTab() {
        _commands.tryEmit(WebViewCommand.GoForward(_uiState.value.activeTab.id))
    }

    fun createNewTab(incognito: Boolean = false, initialUrl: String = BrowserTabState.HOME_URL) {
        val newTab = BrowserTabState(
            title = if (incognito) "Aba Anônima" else "Nova Aba Privada",
            url = initialUrl,
            isDesktopMode = localConfig.value.desktopModeDefault,
            isIncognito = incognito
        )
        _uiState.update { state ->
            val updated = state.tabs + newTab
            state.copy(tabs = updated, activeTabIndex = updated.lastIndex)
        }
        if (initialUrl != BrowserTabState.HOME_URL) {
            _commands.tryEmit(WebViewCommand.LoadUrl(newTab.id, initialUrl))
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { state ->
            if (index in state.tabs.indices) state.copy(activeTabIndex = index) else state
        }
    }

    fun closeTab(index: Int) {
        _uiState.update { state ->
            if (state.tabs.size <= 1) {
                state.copy(tabs = listOf(BrowserTabState()), activeTabIndex = 0)
            } else {
                val removed = state.tabs[index]
                val nextTabs = state.tabs.toMutableList().apply { removeAt(index) }
                val nextIndex = state.activeTabIndex.coerceAtMost(nextTabs.lastIndex)
                if (auth.currentUser != null) {
                    viewModelScope.launch {
                        runCatching { syncRepository.deleteSyncedTab(removed.id) }
                    }
                }
                state.copy(tabs = nextTabs, activeTabIndex = nextIndex)
            }
        }
    }

    fun toggleActiveTabDesktopMode() {
        val tab = _uiState.value.activeTab
        val next = !tab.isDesktopMode
        updateTab(tab.id) { it.copy(isDesktopMode = next) }
        if (tab.url != BrowserTabState.HOME_URL) {
            _commands.tryEmit(WebViewCommand.Reload(tab.id))
        }
        showToast(if (next) "Modo Computador (Desktop) ativado" else "Modo Mobile restaurado")
    }

    fun toggleActiveTabIncognito() {
        val tab = _uiState.value.activeTab
        val next = !tab.isIncognito
        updateTab(tab.id) { it.copy(isIncognito = next) }
        showToast(if (next) "Modo Anônimo Zero-Rastro ativado" else "Modo Padrão Criptografado")
    }

    // --- WebView Callbacks ---

    fun onWebPageStarted(tabId: String, url: String) {
        updateTab(tabId) {
            it.copy(
                url = url,
                isLoading = true,
                progress = 15,
                pageError = null,
                isTranslated = false,
                detectedVideos = emptyList()
            )
        }
    }

    fun onWebPageProgress(tabId: String, progress: Int, canGoBack: Boolean, canGoForward: Boolean) {
        updateTab(tabId) {
            it.copy(
                progress = progress,
                isLoading = progress < 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    fun onWebPageFinished(tabId: String, url: String, title: String, canGoBack: Boolean, canGoForward: Boolean) {
        val cleanTitle = title.ifBlank { Uri.parse(url).host ?: url }
        updateTab(tabId) {
            it.copy(
                url = url,
                title = cleanTitle,
                isLoading = false,
                progress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }

        val tab = _uiState.value.tabs.find { it.id == tabId } ?: return
        if (!tab.isIncognito && url.startsWith("http")) {
            viewModelScope.launch(Dispatchers.IO) {
                dao.insertHistory(
                    HistoryEntity(
                        titleEncrypted = VaultCryptoManager.encrypt(cleanTitle),
                        urlEncrypted = VaultCryptoManager.encrypt(url),
                        host = Uri.parse(url).host.orEmpty()
                    )
                )
                if (auth.currentUser != null) {
                    runCatching {
                        syncRepository.upsertSyncedTab(tabId, cleanTitle, url, isIncognito = false)
                    }
                }
            }
        }
    }

    fun onWebPageError(tabId: String, description: String) {
        updateTab(tabId) {
            it.copy(
                isLoading = false,
                pageError = description
            )
        }
    }

    fun onRequestBlocked(tabId: String, isTrackerOrMiner: Boolean) {
        updateTab(tabId) {
            if (isTrackerOrMiner) {
                it.copy(trackersBlockedOnPage = it.trackersBlockedOnPage + 1)
            } else {
                it.copy(adsBlockedOnPage = it.adsBlockedOnPage + 1)
            }
        }
        viewModelScope.launch {
            localPrefs.incrementBlockedCounters(
                adsDelta = if (isTrackerOrMiner) 0L else 1L,
                trackersDelta = if (isTrackerOrMiner) 1L else 0L
            )
        }
    }

    fun onVideoDetected(tabId: String, media: DetectedVideoMedia) {
        updateTab(tabId) { tab ->
            if (tab.detectedVideos.any { it.streamUrl == media.streamUrl }) {
                tab
            } else {
                tab.copy(detectedVideos = (tab.detectedVideos + media).takeLast(12))
            }
        }
    }

    // --- Automatic AI + Offline Cached Translation ---

    fun onDomExtractedForTranslation(
        tabId: String,
        url: String,
        htmlLang: String?,
        segments: List<String>,
        fullText: String,
        manualTrigger: Boolean = false
    ) {
        updateTab(tabId) { it.copy(lastExtractedText = fullText) }

        val config = localConfig.value
        if (!manualTrigger && !config.autoTranslateEnabled) return
        if (segments.isEmpty()) return
        if (!manualTrigger && translatedUrlCache.contains(tabId)) return

        val detected = translationRepository.detectLanguageHeuristic(
            segments.take(6).joinToString(" "),
            htmlLang
        )
        updateTab(tabId) { it.copy(detectedSourceLang = detected) }

        if (!manualTrigger && detected == config.preferredLanguage) {
            return
        }

        translatedUrlCache.add(tabId)
        viewModelScope.launch {
            updateTab(tabId) { it.copy(isTranslating = true) }
            val result = translationRepository.translatePageSegments(
                segments = segments,
                targetLanguageCode = config.preferredLanguage,
                targetLanguageLabel = config.preferredLanguageLabel,
                htmlLangHint = htmlLang
            )
            result.onSuccess { res ->
                updateTab(tabId) {
                    it.copy(
                        isTranslating = false,
                        detectedSourceLang = res.detectedSourceLanguage,
                        isTranslated = res.detectedSourceLanguage != config.preferredLanguage,
                        translationFromCache = res.fromOfflineCache
                    )
                }
                if (res.detectedSourceLanguage != config.preferredLanguage) {
                    val jsonArr = JSONArray(res.translatedBlocks).toString()
                    _commands.tryEmit(WebViewCommand.ApplyTranslation(tabId, jsonArr))
                }
            }.onFailure { err ->
                updateTab(tabId) { it.copy(isTranslating = false) }
                if (manualTrigger) {
                    showToast(err.localizedMessage ?: "Não foi possível traduzir agora.")
                }
            }
        }
    }

    fun toggleOriginalOrTranslated(tabId: String) {
        val tab = _uiState.value.tabs.find { it.id == tabId } ?: return
        if (tab.isTranslated) {
            updateTab(tabId) { it.copy(isTranslated = false) }
            _commands.tryEmit(WebViewCommand.RestoreOriginal(tabId))
        } else {
            translatedUrlCache.remove(tabId)
            _commands.tryEmit(WebViewCommand.Reload(tabId))
        }
    }

    fun quickTranslateText(text: String) {
        if (text.isBlank()) return
        val cfg = localConfig.value
        viewModelScope.launch {
            _uiState.update { it.copy(isQuickTranslating = true) }
            translationRepository.translateSingleText(
                text = text,
                targetLanguageCode = cfg.preferredLanguage,
                targetLanguageLabel = cfg.preferredLanguageLabel
            ).onSuccess { translated ->
                _uiState.update { it.copy(isQuickTranslating = false, quickTranslateOutput = translated) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isQuickTranslating = false,
                        quickTranslateOutput = "Erro: ${err.localizedMessage ?: "Sem conexão e sem cache para esta frase."}"
                    )
                }
            }
        }
    }

    // --- AI Page Summary & Extension Generator ---

    fun summarizeActivePage() {
        val tab = _uiState.value.activeTab
        val content = tab.lastExtractedText.ifBlank { "Página: ${tab.title} (${tab.url})" }
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingSummary = true, aiSummaryResult = null) }
            translationRepository.summarizePageWithPrivacyAudit(
                pageTitle = tab.title,
                pageUrl = tab.url,
                pageText = content,
                targetLanguageLabel = localConfig.value.preferredLanguageLabel
            ).onSuccess { summary ->
                _uiState.update { it.copy(isGeneratingSummary = false, aiSummaryResult = summary) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGeneratingSummary = false,
                        aiSummaryResult = "Falha na análise AI: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun dismissAiSummary() {
        _uiState.update { it.copy(aiSummaryResult = null) }
    }

    fun generateExtensionWithAi(descriptionPrompt: String) {
        if (descriptionPrompt.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingExtension = true, generatedExtensionDraft = null) }
            translationRepository.generateCustomExtension(descriptionPrompt)
                .onSuccess { draft ->
                    _uiState.update { it.copy(isGeneratingExtension = false, generatedExtensionDraft = draft) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isGeneratingExtension = false) }
                    showToast(err.localizedMessage ?: "Erro ao gerar extensão.")
                }
        }
    }

    fun clearGeneratedExtensionDraft() {
        _uiState.update { it.copy(generatedExtensionDraft = null) }
    }

    // --- Extensions CRUD (Local-first + Optional Cloud Sync) ---

    fun toggleExtension(ext: BrowserExtension) {
        viewModelScope.launch(Dispatchers.IO) {
            val toggled = ext.copy(enabled = !ext.enabled)
            dao.upsertLocalExtension(toggled.toEntity())
            if (auth.currentUser != null) {
                runCatching { syncRepository.saveExtension(toggled) }
            }
        }
    }

    fun saveCustomExtension(
        id: String = "",
        name: String,
        description: String,
        category: String,
        matchPattern: String,
        jsCode: String,
        cssCode: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolvedId = id.ifBlank { "custom_${UUID.randomUUID().toString().take(12)}" }
            val uid = auth.currentUser?.uid.orEmpty()
            val ext = BrowserExtension(
                id = resolvedId,
                userId = uid,
                name = name,
                description = description,
                version = "1.0.0",
                author = "Personalizada",
                category = category,
                matchPattern = matchPattern.ifBlank { "*://*/*" },
                jsCode = jsCode,
                cssCode = cssCode,
                enabled = true,
                isBuiltIn = false,
                runAt = "document_end"
            )
            dao.upsertLocalExtension(ext.toEntity())
            if (auth.currentUser != null) {
                runCatching { syncRepository.saveExtension(ext) }
                showToast("Extensão '$name' instalada e sincronizada!")
            } else {
                showToast("Extensão '$name' instalada localmente!")
            }
        }
    }

    fun deleteExtension(ext: BrowserExtension) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteLocalExtension(ext.id)
            if (auth.currentUser != null) {
                runCatching { syncRepository.deleteExtension(ext.id) }
            }
            showToast("Extensão removida.")
        }
    }

    // --- Bookmarks, Offline Pages & Video Player / Downloader ---

    fun bookmarkActivePage() {
        val tab = _uiState.value.activeTab
        if (tab.url == BrowserTabState.HOME_URL) return
        viewModelScope.launch(Dispatchers.IO) {
            val id = "bm_${UUID.randomUUID().toString().take(12)}"
            dao.upsertLocalBookmark(
                LocalBookmarkEntity(
                    id = id,
                    title = tab.title.ifBlank { tab.url },
                    url = tab.url,
                    folder = "Favoritos"
                )
            )
            if (auth.currentUser != null) {
                runCatching {
                    syncRepository.addBookmark(tab.title, tab.url, "Favoritos", id)
                }
                showToast("Salvo nos Favoritos sincronizados!")
            } else {
                showToast("Salvo nos Favoritos locais!")
            }
        }
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteLocalBookmark(id)
            if (auth.currentUser != null) {
                runCatching { syncRepository.deleteBookmark(id) }
            }
        }
    }

    fun requestSaveActivePageOffline() {
        val tab = _uiState.value.activeTab
        if (tab.url == BrowserTabState.HOME_URL) return
        _commands.tryEmit(WebViewCommand.TriggerSaveOfflineCapture(tab.id))
    }

    fun saveOfflinePageSnapshot(url: String, title: String, html: String, text: String, detectedLang: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertOfflinePage(
                OfflinePageEntity(
                    url = url,
                    title = title.ifBlank { url },
                    htmlContentEncrypted = VaultCryptoManager.encrypt(html.take(400_000)),
                    textSnippetEncrypted = VaultCryptoManager.encrypt(text.take(40_000)),
                    detectedLanguage = detectedLang
                )
            )
            showToast("Página salva com criptografia AES-256 para leitura offline!")
        }
    }

    fun deleteOfflinePage(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteOfflinePage(id)
        }
    }

    fun openVideoPlayer(media: DetectedVideoMedia) {
        _uiState.update { it.copy(activeVideoPlayerMedia = media) }
    }

    fun openLocalVideoInPlayer(entity: DownloadedVideoEntity) {
        openVideoPlayer(
            DetectedVideoMedia(
                streamUrl = Uri.fromFile(File(entity.localFilePath)).toString(),
                title = entity.title,
                pageUrl = entity.pageUrl,
                mimeType = entity.mimeType
            )
        )
    }

    fun closeVideoPlayer() {
        _uiState.update { it.copy(activeVideoPlayerMedia = null) }
    }

    fun downloadVideo(media: DetectedVideoMedia) {
        viewModelScope.launch {
            showToast("Iniciando download local de vídeo...")
            videoDownloader.downloadVideoLocally(media)
                .onSuccess {
                    showToast("Vídeo salvo localmente em Cofre Offline!")
                }
                .onFailure { err ->
                    showToast(err.localizedMessage ?: "Falha no download do vídeo.")
                }
        }
    }

    fun extractAndPlayFromBlockedUrl(pageUrl: String) {
        if (pageUrl.isBlank()) return
        val normalized = if (pageUrl.startsWith("http")) pageUrl else "https://$pageUrl"
        viewModelScope.launch {
            _uiState.update { it.copy(isExtractingBlockedVideo = true) }
            videoDownloader.extractStreamsFromBlockedPage(normalized)
                .onSuccess { found ->
                    _uiState.update { it.copy(isExtractingBlockedVideo = false) }
                    if (found.isNotEmpty()) {
                        found.forEach { onVideoDetected(_uiState.value.activeTab.id, it) }
                        openVideoPlayer(found.first())
                        showToast("${found.size} stream(s) de vídeo extraído(s)!")
                    } else {
                        // Allow user to play direct media stream URL directly
                        if (normalized.contains(".mp4", true) || normalized.contains(".m3u8", true)) {
                            openVideoPlayer(
                                DetectedVideoMedia(
                                    streamUrl = normalized,
                                    title = "Stream Direto",
                                    pageUrl = normalized
                                )
                            )
                        } else {
                            showToast("Nenhum stream de vídeo direto encontrado nesta página.")
                        }
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isExtractingBlockedVideo = false) }
                    showToast("Erro ao extrair vídeo: ${err.localizedMessage}")
                }
        }
    }

    fun exportVideoToGallery(video: DownloadedVideoEntity) {
        viewModelScope.launch {
            videoDownloader.exportToPublicDownloads(video)
                .onSuccess {
                    showToast("Vídeo exportado para Filmes/AegisX no dispositivo!")
                }
                .onFailure { err ->
                    showToast("Falha ao exportar: ${err.localizedMessage}")
                }
        }
    }

    fun deleteDownloadedVideo(video: DownloadedVideoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(video.localFilePath).delete() }
            dao.deleteDownloadedVideo(video.id)
            showToast("Vídeo local removido.")
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearHistory()
            showToast("Histórico criptografado apagado.")
        }
    }

    fun clearTranslationCache() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearTranslationCache()
            showToast("Cache de traduções limpo.")
        }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun updateTab(tabId: String, transform: (BrowserTabState) -> BrowserTabState) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map { if (it.id == tabId) transform(it) else it })
        }
    }

    private fun resolveUrlOrSearch(input: String, engine: String): String {
        val lower = input.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return input
        if (!input.contains(" ") && input.contains(".") && !input.endsWith(".")) {
            return "https://$input"
        }
        val encoded = Uri.encode(input)
        return when (engine) {
            "Brave" -> "https://search.brave.com/search?q=$encoded"
            "Startpage" -> "https://www.startpage.com/sp/search?query=$encoded"
            "Google" -> "https://www.google.com/search?q=$encoded"
            else -> "https://duckduckgo.com/?q=$encoded"
        }
    }

    companion object {
        private const val TAG = "AegisViewModel"
    }
}

private fun LocalExtensionEntity.toDomain(userId: String): BrowserExtension = BrowserExtension(
    id = id,
    userId = userId,
    name = name,
    description = description,
    version = version,
    author = author,
    category = category,
    matchPattern = matchPattern,
    jsCode = jsCode,
    cssCode = cssCode,
    enabled = enabled,
    isBuiltIn = isBuiltIn,
    runAt = runAt
)

private fun BrowserExtension.toEntity(): LocalExtensionEntity = LocalExtensionEntity(
    id = id.ifBlank { "ext_${UUID.randomUUID().toString().take(12)}" },
    name = name,
    description = description,
    version = version,
    author = author,
    category = category,
    matchPattern = matchPattern,
    jsCode = jsCode,
    cssCode = cssCode,
    enabled = enabled,
    isBuiltIn = isBuiltIn,
    runAt = runAt
)

private fun LocalBookmarkEntity.toDomain(userId: String): SyncedBookmark = SyncedBookmark(
    id = id,
    userId = userId,
    title = title,
    url = url,
    folder = folder
)

private fun SyncedBookmark.toEntity(): LocalBookmarkEntity = LocalBookmarkEntity(
    id = id.ifBlank { "bm_${UUID.randomUUID().toString().take(12)}" },
    title = title,
    url = url,
    folder = folder
)
