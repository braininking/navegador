package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.browser.DownloadProgressState
import com.example.data.local.DownloadedVideoEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.OfflinePageEntity
import com.example.data.remote.SyncedBookmark
import com.example.security.VaultCryptoManager
import com.example.ui.AegisUiState
import com.example.ui.AegisViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ShieldEmerald
import java.text.DateFormat
import java.util.Date

@Composable
fun VaultScreen(
    videos: List<DownloadedVideoEntity>,
    offlinePages: List<OfflinePageEntity>,
    bookmarks: List<SyncedBookmark>,
    history: List<HistoryEntity>,
    downloadProgress: DownloadProgressState,
    uiState: AegisUiState,
    viewModel: AegisViewModel,
    onOpenUrlInBrowser: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var blockedPageUrlInput by remember { mutableStateOf("") }
    var previewOfflinePage by remember { mutableStateOf<OfflinePageEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("Cofre Offline & Mídia", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Vídeos baixados, extrator de sites bloqueados e páginas criptografadas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Vídeos (${videos.size})") },
                icon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Offline (${offlinePages.size})") },
                icon = { Icon(Icons.Filled.OfflinePin, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Favoritos") },
                icon = { Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        when (selectedTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Blocked-site Universal Video Extractor Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CyberCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Reprodutor Universal & Extrator de Sites",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CyberCyan
                                )
                                Text(
                                    text = "Cole o link de um vídeo ou página que não abre no celular para extrair, assistir e baixar localmente.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = blockedPageUrlInput,
                                    onValueChange = { blockedPageUrlInput = it },
                                    label = { Text("URL da página ou stream (.mp4 / .m3u8)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("blocked_video_url_input")
                                )
                                Button(
                                    onClick = { viewModel.extractAndPlayFromBlockedUrl(blockedPageUrlInput) },
                                    enabled = blockedPageUrlInput.isNotBlank() && !uiState.isExtractingBlockedVideo,
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianVoid),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (uiState.isExtractingBlockedVideo) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ObsidianVoid)
                                    } else {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Extrair & Reproduzir Agora", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (downloadProgress.isDownloading) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Baixando: ${downloadProgress.fileName} (${downloadProgress.progressPercent}%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ShieldEmerald
                                    )
                                    LinearProgressIndicator(
                                        progress = { downloadProgress.progressPercent / 100f },
                                        color = ShieldEmerald,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    if (videos.isEmpty()) {
                        item {
                            EmptyVaultNotice(
                                title = "Nenhum vídeo salvo localmente",
                                subtitle = "Reproduza vídeos em qualquer página no navegador e toque em Baixar para assistir offline."
                            )
                        }
                    } else {
                        items(videos, key = { it.id }) { video ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val mb = String.format("%.1f MB", video.fileSizeBytes / (1024f * 1024f))
                                            Text(
                                                text = "$mb • Salvo localmente • ${DateFormat.getDateInstance().format(Date(video.downloadedAt))}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ShieldEmerald
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteDownloadedVideo(video) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Remover vídeo", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.openLocalVideoInPlayer(video) },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianVoid),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Assistir Offline")
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.exportVideoToGallery(video) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Exportar")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (offlinePages.isEmpty()) {
                        item {
                            EmptyVaultNotice(
                                title = "Sem páginas offline salvas",
                                subtitle = "No navegador, abra o menu ⋮ e toque em 'Salvar Offline' para ler mesmo sem internet."
                            )
                        }
                    } else {
                        items(offlinePages, key = { it.id }) { page ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { previewOfflinePage = page }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.DownloadDone, contentDescription = null, tint = ShieldEmerald)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(page.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${page.url} • Criptografada AES-256", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { viewModel.deleteOfflinePage(page.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Remover página offline")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text("Favoritos (${bookmarks.size})", style = MaterialTheme.typography.titleMedium, color = CyberCyan)
                    }
                    if (bookmarks.isEmpty()) {
                        item {
                            Text("Toque no ícone de marcador no navegador para salvar seus favoritos localmente (ou sincronizar com sua conta).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(bookmarks, key = { it.id }) { bm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                                    .clickable { onOpenUrlInBrowser(bm.url) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = CyberCyan)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bm.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(bm.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { viewModel.deleteBookmark(bm.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Excluir favorito")
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Histórico Criptografado Local", style = MaterialTheme.typography.titleMedium, color = ShieldEmerald)
                            if (history.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearHistory() }) {
                                    Text("Limpar Tudo")
                                }
                            }
                        }
                    }
                    items(history.take(20), key = { it.id }) { item ->
                        val title = remember(item.id) { VaultCryptoManager.decrypt(item.titleEncrypted) }
                        val url = remember(item.id) { VaultCryptoManager.decrypt(item.urlEncrypted) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .clickable { if (url.isNotBlank()) onOpenUrlInBrowser(url) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title.ifBlank { item.host }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    previewOfflinePage?.let { page ->
        val plainText = remember(page.id) { VaultCryptoManager.decrypt(page.textSnippetEncrypted) }
        AlertDialog(
            onDismissRequest = { previewOfflinePage = null },
            title = { Text(page.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = plainText.ifBlank { "Conteúdo offline criptografado salvo." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = page.url
                    previewOfflinePage = null
                    onOpenUrlInBrowser(target)
                }) {
                    Text("Abrir no Navegador")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewOfflinePage = null }) {
                    Text("Fechar")
                }
            }
        )
    }
}

@Composable
private fun EmptyVaultNotice(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
