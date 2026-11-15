package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.browser.VideoDownloader
import com.example.data.local.AegisDatabase
import com.example.data.local.LocalPreferencesStore
import com.example.data.remote.AegisSyncRepository
import com.example.data.remote.TranslationAiRepository
import com.example.security.configureAppCheck
import com.example.ui.AegisViewModel
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.ExtensionsScreen
import com.example.ui.screens.LanguageOnboardingOverlay
import com.example.ui.screens.ShieldProfileScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.screens.VideoPlayerModal
import com.example.ui.theme.AegisXTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class AegisTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    BROWSER("Navegar", Icons.Filled.Public, Icons.Outlined.Public, "nav_browser"),
    EXTENSIONS("Extensões", Icons.Filled.Extension, Icons.Outlined.Extension, "nav_extensions"),
    VAULT("Cofre", Icons.Filled.FolderSpecial, Icons.Outlined.FolderSpecial, "nav_vault"),
    SHIELD("Escudo", Icons.Filled.Shield, Icons.Outlined.Shield, "nav_shield")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration before Compose UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefsStore = remember { LocalPreferencesStore(context.applicationContext) }
            val localConfig by prefsStore.configFlow.collectAsStateWithLifecycle(
                initialValue = com.example.data.local.LocalBrowserConfig()
            )

            AegisXTheme(darkTheme = localConfig.darkThemeEnabled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    // Open the browser immediately without forcing login; account sync is optional in Shield/Profile.
    AegisBrowserRoot(auth = auth)
}

@Composable
fun AegisBrowserRoot(auth: FirebaseAuth = Firebase.auth) {
    val viewModel: AegisViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(databaseId)
                val roomDb = AegisDatabase.getInstance(app)
                val dao = roomDb.aegisDao()
                AegisViewModel(
                    syncRepository = AegisSyncRepository(firestore),
                    translationRepository = TranslationAiRepository(dao),
                    localPrefs = LocalPreferencesStore(app),
                    dao = dao,
                    videoDownloader = VideoDownloader(app, dao),
                    auth = auth
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val config by viewModel.localConfig.collectAsStateWithLifecycle()
    val vpnStatus by viewModel.vpnStatus.collectAsStateWithLifecycle()
    val extensions by viewModel.extensions.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val syncedTabs by viewModel.syncedTabs.collectAsStateWithLifecycle()
    val offlinePages by viewModel.offlinePages.collectAsStateWithLifecycle()
    val downloadedVideos by viewModel.downloadedVideos.collectAsStateWithLifecycle()
    val history by viewModel.historyEntries.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val translationCacheCount by viewModel.translationCacheCount.collectAsStateWithLifecycle()

    // First-launch Language Onboarding Gate
    if (!config.onboardingCompleted) {
        LanguageOnboardingOverlay(
            initialLanguageCode = config.preferredLanguage,
            onConfirm = { code, label, autoTranslate ->
                viewModel.completeLanguageOnboarding(code, label, autoTranslate)
            }
        )
        return
    }

    var currentTab by remember { mutableStateOf(AegisTab.BROWSER) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    if (currentTab != AegisTab.BROWSER) {
        BackHandler {
            currentTab = AegisTab.BROWSER
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isExpanded) {
                    NavigationBar {
                        AegisTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpanded) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        AegisTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentTab) {
                        AegisTab.BROWSER -> BrowserScreen(
                            uiState = uiState,
                            config = config,
                            vpnStatus = vpnStatus,
                            extensions = extensions,
                            bookmarks = bookmarks,
                            syncedTabs = syncedTabs,
                            viewModel = viewModel,
                            onNavigateToShield = { currentTab = AegisTab.SHIELD }
                        )
                        AegisTab.EXTENSIONS -> ExtensionsScreen(
                            extensions = extensions,
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        AegisTab.VAULT -> VaultScreen(
                            videos = downloadedVideos,
                            offlinePages = offlinePages,
                            bookmarks = bookmarks,
                            history = history,
                            downloadProgress = downloadProgress,
                            uiState = uiState,
                            viewModel = viewModel,
                            onOpenUrlInBrowser = { url ->
                                viewModel.openUrlInBrowser(url)
                                currentTab = AegisTab.BROWSER
                            }
                        )
                        AegisTab.SHIELD -> ShieldProfileScreen(
                            config = config,
                            vpnStatus = vpnStatus,
                            translationCacheCount = translationCacheCount,
                            currentUser = currentUser,
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    // Universal Video Player Overlay Modal
    uiState.activeVideoPlayerMedia?.let { media ->
        VideoPlayerModal(
            media = media,
            downloadProgress = downloadProgress,
            onDownloadLocal = { viewModel.downloadVideo(media) },
            onClose = { viewModel.closeVideoPlayer() }
        )
    }
}
