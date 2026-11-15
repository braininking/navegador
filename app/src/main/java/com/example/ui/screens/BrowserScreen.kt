package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.browser.DetectedVideoMedia
import com.example.browser.ExtensionCatalog
import com.example.data.local.LocalBrowserConfig
import com.example.data.remote.BrowserExtension
import com.example.data.remote.SyncedBookmark
import com.example.data.remote.SyncedTab
import com.example.security.AdBlockEngine
import com.example.security.BlockCategory
import com.example.security.VpnStatus
import com.example.ui.AegisUiState
import com.example.ui.AegisViewModel
import com.example.ui.BrowserTabState
import com.example.ui.WebViewCommand
import com.example.ui.components.AegisXBrandBadge
import com.example.ui.components.SecurityStatusChip
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberIndigo
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ShieldEmerald
import org.json.JSONArray

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

private class AegisJsBridge(
    private val tabId: String,
    private val viewModel: AegisViewModel
) {
    @JavascriptInterface
    fun onDomExtracted(htmlLang: String, segmentsJson: String, fullText: String, manual: Boolean) {
        val list = runCatching {
            val arr = JSONArray(segmentsJson)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
        viewModel.onDomExtractedForTranslation(
            tabId = tabId,
            url = "",
            htmlLang = htmlLang,
            segments = list,
            fullText = fullText,
            manualTrigger = manual
        )
    }

    @JavascriptInterface
    fun onVideoDetected(streamUrl: String, title: String, pageUrl: String, mimeType: String) {
        if (streamUrl.startsWith("http")) {
            viewModel.onVideoDetected(
                tabId,
                DetectedVideoMedia(
                    streamUrl = streamUrl,
                    title = title.ifBlank { "Vídeo da Página" },
                    pageUrl = pageUrl,
                    mimeType = mimeType.ifBlank { "video/mp4" }
                )
            )
        }
    }

    @JavascriptInterface
    fun onOfflineCapture(url: String, title: String, html: String, text: String, lang: String) {
        viewModel.saveOfflinePageSnapshot(url, title, html, text, lang)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    uiState: AegisUiState,
    config: LocalBrowserConfig,
    vpnStatus: VpnStatus,
    extensions: List<BrowserExtension>,
    bookmarks: List<SyncedBookmark>,
    syncedTabs: List<SyncedTab>,
    viewModel: AegisViewModel,
    onNavigateToShield: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val activeTab = uiState.activeTab

    var urlInput by remember(activeTab.id, activeTab.url) {
        mutableStateOf(if (activeTab.url == BrowserTabState.HOME_URL) "" else activeTab.url)
    }
    var showTabsSheet by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }

    val webViews = remember { mutableStateMapOf<String, WebView>() }

    // Dispose closed WebViews cleanly
    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { wv ->
                wv.stopLoading()
                wv.destroy()
            }
            webViews.clear()
        }
    }

    // Handle commands emitted from ViewModel
    LaunchedEffect(Unit) {
        viewModel.commands.collect { cmd ->
            when (cmd) {
                is WebViewCommand.LoadUrl -> {
                    webViews[cmd.tabId]?.let { wv ->
                        configureWebViewPrivacy(wv, activeTab, config, context)
                        wv.loadUrl(
                            cmd.url,
                            mapOf("DNT" to "1", "Sec-GPC" to "1")
                        )
                    }
                }
                is WebViewCommand.Reload -> {
                    webViews[cmd.tabId]?.let { wv ->
                        configureWebViewPrivacy(wv, activeTab, config, context)
                        wv.reload()
                    }
                }
                is WebViewCommand.GoBack -> {
                    val wv = webViews[cmd.tabId]
                    if (wv != null && wv.canGoBack()) {
                        wv.goBack()
                    } else {
                        viewModel.goHome()
                    }
                }
                is WebViewCommand.GoForward -> {
                    webViews[cmd.tabId]?.takeIf { it.canGoForward() }?.goForward()
                }
                is WebViewCommand.ApplyTranslation -> {
                    val safeJson = cmd.translationsJsonArray
                    webViews[cmd.tabId]?.evaluateJavascript(
                        "if (window.__aegisApplyTranslations) { window.__aegisApplyTranslations($safeJson); }",
                        null
                    )
                }
                is WebViewCommand.RestoreOriginal -> {
                    webViews[cmd.tabId]?.evaluateJavascript(
                        "if (window.__aegisRestoreOriginals) { window.__aegisRestoreOriginals(); }",
                        null
                    )
                }
                is WebViewCommand.InjectExtensions -> {
                    webViews[cmd.tabId]?.evaluateJavascript(cmd.script, null)
                }
                is WebViewCommand.TriggerSaveOfflineCapture -> {
                    webViews[cmd.tabId]?.evaluateJavascript(
                        """
                        (function(){
                          var html = document.documentElement ? document.documentElement.outerHTML : '';
                          var text = document.body ? document.body.innerText : '';
                          var lang = document.documentElement.lang || 'auto';
                          AegisBridge.onOfflineCapture(window.location.href, document.title || window.location.href, html, text, lang);
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Futuristic Omnibox Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // VPN / Shield button
                    IconButton(
                        onClick = onNavigateToShield,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (vpnStatus.isConnected) ShieldEmerald.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .testTag("omnibox_shield_button")
                    ) {
                        Icon(
                            imageVector = if (vpnStatus.isConnected) Icons.Filled.VpnLock else Icons.Filled.Shield,
                            contentDescription = "Status VPN e Escudo",
                            tint = if (vpnStatus.isConnected) ShieldEmerald else CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Address / Search Omnibox
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (activeTab.isIncognito) Color(0xFF1A142B)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (activeTab.isIncognito) CyberIndigo
                                else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                activeTab.isIncognito -> Icons.Filled.VisibilityOff
                                activeTab.url.startsWith("https://") -> Icons.Filled.Lock
                                else -> Icons.Filled.Search
                            },
                            contentDescription = null,
                            tint = if (activeTab.url.startsWith("https://")) ShieldEmerald else CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )

                        BasicTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(CyberCyan),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    focusManager.clearFocus()
                                    viewModel.navigateToInput(urlInput)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("url_omnibox_input"),
                            decorationBox = { innerTextField ->
                                if (urlInput.isEmpty()) {
                                    Text(
                                        text = "Pesquisar com ${config.searchEngine} ou URL...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (activeTab.adsBlockedOnPage + activeTab.trackersBlockedOnPage > 0) {
                            Box(
                                modifier = Modifier
                                    .background(ShieldEmerald.copy(alpha = 0.16f), RoundedCornerShape(50))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${activeTab.adsBlockedOnPage + activeTab.trackersBlockedOnPage}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ShieldEmerald
                                )
                            }
                        }

                        if (urlInput.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    if (activeTab.url != BrowserTabState.HOME_URL) {
                                        viewModel.reloadActiveTab()
                                    } else {
                                        urlInput = ""
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (activeTab.url != BrowserTabState.HOME_URL) Icons.Filled.Refresh else Icons.Filled.Close,
                                    contentDescription = "Recarregar ou limpar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    // Tabs pill button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .clickable { showTabsSheet = true }
                            .testTag("tabs_switcher_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${uiState.tabs.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberCyan
                        )
                    }
                }

                if (activeTab.isLoading) {
                    LinearProgressIndicator(
                        progress = { (activeTab.progress / 100f).coerceIn(0.05f, 1f) },
                        color = CyberCyan,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                    )
                }
            }
        }

        // Translation Status Pill
        AnimatedVisibility(
            visible = activeTab.isTranslating || activeTab.isTranslated
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberCyan.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeTab.isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = CyberCyan,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Traduzindo página para ${config.preferredLanguageLabel}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        val src = activeTab.detectedSourceLang?.uppercase() ?: "AUTO"
                        val cacheLabel = if (activeTab.translationFromCache) " • Cache Offline" else " • IA Gemini"
                        Text(
                            text = "Traduzido ($src → ${config.preferredLanguage.uppercase()})$cacheLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan
                        )
                    }
                }
                if (activeTab.isTranslated) {
                    Text(
                        text = "Ver original",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clickable { viewModel.toggleOriginalOrTranslated(activeTab.id) }
                            .padding(4.dp)
                    )
                }
            }
        }

        // Main Content: Start Page or Live Chromium WebView
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (activeTab.url == BrowserTabState.HOME_URL) {
                AegisStartPage(
                    config = config,
                    vpnStatus = vpnStatus,
                    activeTab = activeTab,
                    bookmarks = bookmarks,
                    syncedTabs = syncedTabs,
                    extensions = extensions,
                    onQuickNavigate = { url -> viewModel.openUrlInBrowser(url) },
                    onNavigateToShield = onNavigateToShield
                )
            } else {
                // Keep AndroidView per active tab ID cleanly
                keyedWebViewContainer(
                    tab = activeTab,
                    config = config,
                    extensions = extensions,
                    viewModel = viewModel,
                    webViews = webViews
                )

                // Error fallback bar for sites that fail to open
                if (activeTab.pageError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Site indisponível ou bloqueado",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = activeTab.pageError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { viewModel.reloadActiveTab() }) {
                                    Text("Tentar Novamente")
                                }
                                Button(
                                    onClick = { viewModel.extractAndPlayFromBlockedUrl(activeTab.url) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberCyan,
                                        contentColor = ObsidianVoid
                                    )
                                ) {
                                    Text("Extrair Vídeo")
                                }
                            }
                        }
                    }
                }
            }

            // Floating Video Detected Pill (Universal Player + Local Downloader)
            androidx.compose.animation.AnimatedVisibility(
                visible = activeTab.detectedVideos.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                val latestVideo = activeTab.detectedVideos.last()
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ObsidianVoid.copy(alpha = 0.94f),
                    shadowElevation = 8.dp,
                    modifier = Modifier.border(
                        1.dp,
                        Brush.horizontalGradient(listOf(CyberCyan, CyberIndigo)),
                        RoundedCornerShape(50)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = CyberCyan
                        )
                        Text(
                            text = "Vídeo detectado (${activeTab.detectedVideos.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Button(
                            onClick = { viewModel.openVideoPlayer(latestVideo) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Reproduzir", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        IconButton(
                            onClick = { viewModel.downloadVideo(latestVideo) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(ShieldEmerald.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Baixar vídeo localmente",
                                tint = ShieldEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Browser Controls Strip
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.goBackActiveTab() },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
                IconButton(
                    onClick = { viewModel.goForwardActiveTab() },
                    enabled = activeTab.canGoForward,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Avançar")
                }
                IconButton(
                    onClick = { viewModel.goHome() },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Home, contentDescription = "Início AegisX")
                }
                IconButton(
                    onClick = { viewModel.bookmarkActivePage() },
                    enabled = activeTab.url != BrowserTabState.HOME_URL,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = "Favoritar página")
                }
                IconButton(
                    onClick = { showActionsSheet = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("browser_more_actions_button")
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Mais ferramentas")
                }
            }
        }
    }

    // Tabs Switcher Sheet (Local + Cross-Device Synced Tabs)
    if (showTabsSheet) {
        ModalBottomSheet(onDismissRequest = { showTabsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Abas Abertas (${uiState.tabs.size})", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            viewModel.createNewTab(incognito = true)
                            showTabsSheet = false
                        }) {
                            Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Anônima")
                        }
                        Button(
                            onClick = {
                                viewModel.createNewTab(incognito = false)
                                showTabsSheet = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianVoid)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Nova Aba")
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.tabs) { index, tab ->
                        val isCurrent = index == uiState.activeTabIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isCurrent) CyberCyan.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (isCurrent) CyberCyan else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    viewModel.selectTab(index)
                                    showTabsSheet = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (tab.isIncognito) Icons.Filled.VisibilityOff else Icons.Filled.Tab,
                                contentDescription = null,
                                tint = if (isCurrent) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tab.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(tab.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { viewModel.closeTab(index) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Fechar aba")
                            }
                        }
                    }

                    if (syncedTabs.isNotEmpty()) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                text = "Sincronizadas em outros dispositivos",
                                style = MaterialTheme.typography.labelLarge,
                                color = CyberCyan
                            )
                        }
                        items(syncedTabs.take(6), key = { it.id }) { sTab ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        viewModel.createNewTab(incognito = false, initialUrl = sTab.url)
                                        showTabsSheet = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sTab.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${sTab.deviceName} • ${sTab.url}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Browser Tools Bottom Sheet
    if (showActionsSheet) {
        ModalBottomSheet(onDismissRequest = { showActionsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Ferramentas AegisX", style = MaterialTheme.typography.titleLarge)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolActionCard(
                        icon = Icons.Filled.Computer,
                        label = if (activeTab.isDesktopMode) "Modo Mobile" else "Modo Computador",
                        highlighted = activeTab.isDesktopMode,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.toggleActiveTabDesktopMode()
                            showActionsSheet = false
                        }
                    )
                    ToolActionCard(
                        icon = Icons.Filled.VisibilityOff,
                        label = if (activeTab.isIncognito) "Sair Anônimo" else "Aba Anônima",
                        highlighted = activeTab.isIncognito,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.toggleActiveTabIncognito()
                            showActionsSheet = false
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolActionCard(
                        icon = Icons.Filled.Translate,
                        label = "Traduzir Página",
                        highlighted = activeTab.isTranslated,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            webViews[activeTab.id]?.let { wv ->
                                extractDomForTranslation(wv, manual = true)
                            }
                            showActionsSheet = false
                        }
                    )
                    ToolActionCard(
                        icon = Icons.Filled.SaveAlt,
                        label = "Salvar Offline",
                        highlighted = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.requestSaveActivePageOffline()
                            showActionsSheet = false
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolActionCard(
                        icon = Icons.Filled.AutoAwesome,
                        label = "Resumo + Auditoria IA",
                        highlighted = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.summarizeActivePage()
                            showActionsSheet = false
                        }
                    )
                    ToolActionCard(
                        icon = Icons.Filled.PlayArrow,
                        label = "Extrair Vídeo/Stream",
                        highlighted = activeTab.detectedVideos.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (activeTab.detectedVideos.isNotEmpty()) {
                                viewModel.openVideoPlayer(activeTab.detectedVideos.last())
                            } else if (activeTab.url != BrowserTabState.HOME_URL) {
                                viewModel.extractAndPlayFromBlockedUrl(activeTab.url)
                            }
                            showActionsSheet = false
                        }
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    // AI Summary Modal
    if (uiState.isGeneratingSummary || uiState.aiSummaryResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAiSummary() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = CyberCyan)
                    Text("Análise de Privacidade & Resumo IA")
                }
            },
            text = {
                if (uiState.isGeneratingSummary) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = CyberCyan, modifier = Modifier.size(24.dp))
                        Text("Analisando conteúdo da página com Gemini...")
                    }
                } else {
                    Text(uiState.aiSummaryResult.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAiSummary() }) {
                    Text("Fechar")
                }
            }
        )
    }
}

@Composable
private fun ToolActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (highlighted) CyberCyan.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                1.dp,
                if (highlighted) CyberCyan else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (highlighted) CyberCyan else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AegisStartPage(
    config: LocalBrowserConfig,
    vpnStatus: VpnStatus,
    activeTab: BrowserTabState,
    bookmarks: List<SyncedBookmark>,
    syncedTabs: List<SyncedTab>,
    extensions: List<BrowserExtension>,
    onQuickNavigate: (String) -> Unit,
    onNavigateToShield: () -> Unit
) {
    val activeExtCount = extensions.count { it.enabled }
    val quickSites = remember {
        listOf(
            "DuckDuckGo" to "https://duckduckgo.com",
            "Wikipedia EN" to "https://en.wikipedia.org/wiki/Internet_privacy",
            "MDN Web Docs" to "https://developer.mozilla.org/en-US/docs/Web/Privacy",
            "BigBuck Video" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AegisXBrandBadge()
                SecurityStatusChip(
                    label = if (vpnStatus.isConnected) "VPN ${vpnStatus.activeNode.countryCode}" else "AES-256",
                    isActive = vpnStatus.isConnected
                )
            }
        }

        // Security Telemetry Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(CyberCyan.copy(alpha = 0.5f), CyberIndigo.copy(alpha = 0.4f))),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onNavigateToShield() }
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeTab.isIncognito) "Sessão Anônima Blindada" else "Escudo de Privacidade Ativo",
                            style = MaterialTheme.typography.titleLarge,
                            color = CyberCyan
                        )
                        Text(
                            text = "Idioma: ${config.preferredLanguageLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ShieldEmerald
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryStat(
                            value = "${config.adsBlockedTotal}",
                            label = "Anúncios Bloqueados"
                        )
                        TelemetryStat(
                            value = "${config.trackersBlockedTotal}",
                            label = "Rastreadores"
                        )
                        TelemetryStat(
                            value = "$activeExtCount",
                            label = "Plugins Ativos"
                        )
                    }
                }
            }
        }

        // Quick Access Sites (including English test site for auto-translation & sample MP4)
        item {
            Text(
                text = "Acesso Rápido & Teste de Tradução / Mídia",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                quickSites.forEach { (label, url) ->
                    AssistChip(
                        onClick = { onQuickNavigate(url) },
                        label = { Text(label) }
                    )
                }
            }
        }

        // Synced Bookmarks
        if (bookmarks.isNotEmpty()) {
            item {
                Text(
                    text = "Favoritos Sincronizados",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(bookmarks, key = { it.id }) { bm ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .width(170.dp)
                                .clickable { onQuickNavigate(bm.url) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(bm.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(bm.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // Cross-device Synced Tabs
        if (syncedTabs.isNotEmpty()) {
            item {
                Text(
                    text = "Continuar de Outro Dispositivo",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(syncedTabs.take(4), key = { it.id }) { tab ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onQuickNavigate(tab.url) }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tab.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${tab.deviceName} • ${tab.url}", style = MaterialTheme.typography.labelSmall, color = CyberCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun keyedWebViewContainer(
    tab: BrowserTabState,
    config: LocalBrowserConfig,
    extensions: List<BrowserExtension>,
    viewModel: AegisViewModel,
    webViews: MutableMap<String, WebView>
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            webViews.getOrPut(tab.id) {
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    addJavascriptInterface(AegisJsBridge(tab.id, viewModel), "AegisBridge")
                    configureWebViewPrivacy(this, tab, config, ctx)

                    setDownloadListener(DownloadListener { url, _, _, mimeType, _ ->
                        if (AdBlockEngine.isVideoResourceUrl(url) || mimeType.startsWith("video/")) {
                            viewModel.downloadVideo(
                                DetectedVideoMedia(
                                    streamUrl = url,
                                    title = title ?: "Vídeo Baixado",
                                    pageUrl = this.url ?: url,
                                    mimeType = mimeType.ifBlank { "video/mp4" }
                                )
                            )
                        } else {
                            viewModel.showToast("Download detectado: $url")
                        }
                    })

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            viewModel.onWebPageProgress(
                                tabId = tab.id,
                                progress = newProgress,
                                canGoBack = view?.canGoBack() ?: false,
                                canGoForward = view?.canGoForward() ?: false
                            )
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            if (url != null) {
                                viewModel.onWebPageStarted(tab.id, url)
                            }
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null
                            val pageHost = runCatching { Uri.parse(tab.url).host }.getOrNull()

                            val blockCat = AdBlockEngine.classifyRequest(
                                url = reqUrl,
                                pageHost = pageHost,
                                adBlockEnabled = config.adBlockEnabled,
                                strictTrackerBlock = config.strictTrackerBlock
                            )
                            if (blockCat != BlockCategory.NONE) {
                                viewModel.onRequestBlocked(
                                    tabId = tab.id,
                                    isTrackerOrMiner = blockCat != BlockCategory.AD
                                )
                                return AdBlockEngine.emptyBlockedResponse()
                            }

                            if (AdBlockEngine.isVideoResourceUrl(reqUrl)) {
                                viewModel.onVideoDetected(
                                    tab.id,
                                    DetectedVideoMedia(
                                        streamUrl = reqUrl,
                                        title = tab.title.ifBlank { "Stream de Vídeo" },
                                        pageUrl = tab.url,
                                        mimeType = if (reqUrl.contains(".m3u8", true)) "application/x-mpegURL" else "video/mp4"
                                    )
                                )
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (view == null || url == null) return
                            viewModel.onWebPageFinished(
                                tabId = tab.id,
                                url = url,
                                title = view.title.orEmpty(),
                                canGoBack = view.canGoBack(),
                                canGoForward = view.canGoForward()
                            )

                            // 1. Inject active open-source & custom JS/CSS extensions
                            val extScript = ExtensionCatalog.buildCombinedInjectionScript(extensions, url)
                            if (extScript.isNotBlank()) {
                                view.evaluateJavascript(extScript, null)
                            }

                            // 2. Inject Universal Video Sniffer
                            view.evaluateJavascript(VIDEO_SNIFFER_JS, null)

                            // 3. Extract DOM text segments for Automatic Translation
                            extractDomForTranslation(view, manual = false)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                viewModel.onWebPageError(
                                    tab.id,
                                    error?.description?.toString() ?: "Erro ao carregar página."
                                )
                            }
                        }
                    }

                    if (tab.url != BrowserTabState.HOME_URL) {
                        loadUrl(tab.url, mapOf("DNT" to "1", "Sec-GPC" to "1"))
                    }
                }
            }
        },
        update = { wv ->
            configureWebViewPrivacy(wv, tab, config, context)
        },
        modifier = Modifier.fillMaxSize()
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureWebViewPrivacy(
    wv: WebView,
    tab: BrowserTabState,
    config: LocalBrowserConfig,
    context: android.content.Context
) {
    wv.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = !tab.isIncognito
        mediaPlaybackRequiresUserGesture = false
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        userAgentString = if (tab.isDesktopMode) DESKTOP_UA else MOBILE_UA
        cacheMode = if (isNetworkConnected(context)) {
            if (tab.isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        } else {
            // Automatic offline agility mode!
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        }
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(wv, !config.strictTrackerBlock && !tab.isIncognito)
}

private fun isNetworkConnected(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun extractDomForTranslation(webView: WebView, manual: Boolean) {
    val js = """
        (function() {
          try {
            if (!window.__aegisOriginals) {
              window.__aegisOriginals = [];
              window.__aegisNodes = [];
            }
            var nodes = [];
            var texts = [];
            var walker = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_TEXT, null, false);
            var node;
            while ((node = walker.nextNode()) && texts.length < 40) {
              var parentTag = node.parentElement ? node.parentElement.tagName : '';
              if (parentTag === 'SCRIPT' || parentTag === 'STYLE' || parentTag === 'NOSCRIPT' || parentTag === 'CODE') continue;
              var t = (node.nodeValue || '').trim();
              if (t.length >= 4 && !/^[\d\s\W]+$/.test(t)) {
                nodes.push(node);
                texts.push(t);
              }
            }
            window.__aegisNodes = nodes;
            window.__aegisOriginals = texts.slice();
            window.__aegisApplyTranslations = function(translatedArr) {
              if (!Array.isArray(translatedArr)) return;
              for (var i = 0; i < Math.min(window.__aegisNodes.length, translatedArr.length); i++) {
                if (translatedArr[i]) window.__aegisNodes[i].nodeValue = translatedArr[i];
              }
            };
            window.__aegisRestoreOriginals = function() {
              for (var i = 0; i < Math.min(window.__aegisNodes.length, window.__aegisOriginals.length); i++) {
                window.__aegisNodes[i].nodeValue = window.__aegisOriginals[i];
              }
            };
            var fullText = (document.body ? document.body.innerText : '').slice(0, 6500);
            var htmlLang = document.documentElement.lang || '';
            AegisBridge.onDomExtracted(htmlLang, JSON.stringify(texts), fullText, $manual);
          } catch (e) {}
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

private val VIDEO_SNIFFER_JS = """
    (function() {
      function scanVideos() {
        var title = document.title || 'Vídeo Detectado';
        var pageUrl = window.location.href;
        document.querySelectorAll('video').forEach(function(v) {
          var src = v.currentSrc || v.src;
          if (src && src.startsWith('http')) {
            AegisBridge.onVideoDetected(src, title, pageUrl, 'video/mp4');
          }
          v.querySelectorAll('source').forEach(function(s) {
            if (s.src && s.src.startsWith('http')) {
              AegisBridge.onVideoDetected(s.src, title, pageUrl, s.type || 'video/mp4');
            }
          });
        });
      }
      scanVideos();
      setTimeout(scanVideos, 2000);
    })();
""".trimIndent()
