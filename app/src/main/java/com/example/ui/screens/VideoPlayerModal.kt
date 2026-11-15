package com.example.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.browser.DetectedVideoMedia
import com.example.browser.DownloadProgressState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ShieldEmerald
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerModal(
    media: DetectedVideoMedia,
    downloadProgress: DownloadProgressState,
    onDownloadLocal: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    val exoPlayer = remember(media.streamUrl) {
        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val headers = buildMap {
            put(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )
            if (media.referer.isNotBlank()) put("Referer", media.referer)
        }
        val httpFactory = OkHttpDataSource.Factory(okHttpClient).setDefaultRequestProperties(headers)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(media.streamUrl))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianVoid)
                .safeDrawingPadding(),
            color = ObsidianVoid
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AEGISX UNIVERSAL PLAYER",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan
                        )
                        Text(
                            text = media.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("close_video_player_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fechar reprodutor",
                            tint = Color.White
                        )
                    }
                }

                // Media3 ExoPlayer Video View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black)
                        .border(1.dp, CyberCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Speed Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                    listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        FilterChip(
                            selected = playbackSpeed == speed,
                            onClick = {
                                playbackSpeed = speed
                                exoPlayer.playbackParameters = PlaybackParameters(speed)
                            },
                            label = { Text("${speed}x", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Stream URL info card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSurface, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Fonte de Stream (Desktop Headers + Referer Bypass)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ShieldEmerald
                    )
                    Text(
                        text = media.streamUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (downloadProgress.isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Baixando localmente: ${downloadProgress.progressPercent}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberCyan
                        )
                        LinearProgressIndicator(
                            progress = { downloadProgress.progressPercent / 100f },
                            color = CyberCyan,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (!media.streamUrl.startsWith("file://")) {
                    Button(
                        onClick = onDownloadLocal,
                        enabled = !downloadProgress.isDownloading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ShieldEmerald,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("player_download_local_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = if (downloadProgress.isDownloading) "Salvando no Cofre..." else "Baixar Vídeo Localmente",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
