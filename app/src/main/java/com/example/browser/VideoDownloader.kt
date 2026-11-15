package com.example.browser

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.AegisDao
import com.example.data.local.DownloadedVideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class DownloadProgressState(
    val isDownloading: Boolean = false,
    val fileName: String = "",
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null
)

data class DetectedVideoMedia(
    val streamUrl: String,
    val title: String,
    val pageUrl: String,
    val mimeType: String = "video/mp4",
    val referer: String = pageUrl
)

class VideoDownloader(
    private val context: Context,
    private val dao: AegisDao
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _progress = MutableStateFlow(DownloadProgressState())
    val progress: StateFlow<DownloadProgressState> = _progress.asStateFlow()

    suspend fun downloadVideoLocally(media: DetectedVideoMedia): Result<DownloadedVideoEntity> =
        withContext(Dispatchers.IO) {
            val videosDir = File(context.filesDir, "videos").apply { mkdirs() }
            val extension = when {
                media.streamUrl.contains(".webm", ignoreCase = true) -> "webm"
                media.streamUrl.contains(".mov", ignoreCase = true) -> "mov"
                media.streamUrl.contains(".mkv", ignoreCase = true) -> "mkv"
                else -> "mp4"
            }
            val safeTitle = media.title
                .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                .trim()
                .replace(" ", "_")
                .take(40)
                .ifBlank { "aegisx_video" }
            val fileName = "${safeTitle}_${System.currentTimeMillis()}.$extension"
            val targetFile = File(videosDir, fileName)

            _progress.value = DownloadProgressState(
                isDownloading = true,
                fileName = fileName,
                progressPercent = 0
            )

            try {
                if (media.streamUrl.contains(".m3u8", ignoreCase = true)) {
                    // For HLS playlists, fetch the playlist and download first/selected media segment or manifest
                    val resolvedUrl = resolveHlsPlayableSegment(media.streamUrl, media.referer)
                        ?: media.streamUrl
                    return@withContext executeStreamDownload(
                        url = resolvedUrl,
                        media = media,
                        fileName = fileName,
                        targetFile = targetFile
                    )
                }
                executeStreamDownload(
                    url = media.streamUrl,
                    media = media,
                    fileName = fileName,
                    targetFile = targetFile
                )
            } catch (e: Exception) {
                Log.e(TAG, "Video download failed", e)
                targetFile.delete()
                _progress.value = DownloadProgressState(
                    isDownloading = false,
                    fileName = fileName,
                    error = e.localizedMessage ?: "Falha no download do vídeo."
                )
                Result.failure(e)
            }
        }

    private suspend fun executeStreamDownload(
        url: String,
        media: DetectedVideoMedia,
        fileName: String,
        targetFile: File
    ): Result<DownloadedVideoEntity> {
        val requestBuilder = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )
        if (media.referer.isNotBlank()) {
            requestBuilder.header("Referer", media.referer)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: não foi possível baixar o stream.")
            }
            val body = response.body ?: throw IllegalStateException("Resposta de vídeo vazia.")
            val contentLength = body.contentLength()
            val contentType = response.header("Content-Type") ?: media.mimeType

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        val pct = if (contentLength > 0) {
                            ((bytesCopied * 100) / contentLength).toInt().coerceIn(0, 100)
                        } else {
                            50
                        }
                        _progress.value = DownloadProgressState(
                            isDownloading = true,
                            fileName = fileName,
                            progressPercent = pct,
                            downloadedBytes = bytesCopied,
                            totalBytes = contentLength
                        )
                    }
                    output.flush()
                }
            }

            val entity = DownloadedVideoEntity(
                title = media.title.ifBlank { fileName },
                sourceUrl = media.streamUrl,
                pageUrl = media.pageUrl,
                localFilePath = targetFile.absolutePath,
                mimeType = contentType.substringBefore(';').ifBlank { "video/mp4" },
                fileSizeBytes = targetFile.length()
            )
            val insertedId = dao.insertDownloadedVideo(entity)
            _progress.value = DownloadProgressState(
                isDownloading = false,
                fileName = fileName,
                progressPercent = 100,
                downloadedBytes = targetFile.length(),
                totalBytes = targetFile.length()
            )
            return Result.success(entity.copy(id = insertedId))
        }
    }

    private fun resolveHlsPlayableSegment(m3u8Url: String, referer: String): String? {
        return try {
            val req = Request.Builder()
                .url(m3u8Url)
                .apply { if (referer.isNotBlank()) header("Referer", referer) }
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val baseUri = Uri.parse(m3u8Url)
                val firstStreamLine = body.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    ?: return null
                if (firstStreamLine.startsWith("http")) {
                    firstStreamLine
                } else {
                    val parent = m3u8Url.substringBeforeLast('/')
                    "$parent/${firstStreamLine.removePrefix("/")}"
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fallback extractor for pages that fail to load or restrict embedded playback in mobile WebView.
     * Fetches the raw HTML using desktop headers and parses video tags, og:video, or direct mp4/m3u8 URLs.
     */
    suspend fun extractStreamsFromBlockedPage(pageUrl: String): Result<List<DetectedVideoMedia>> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(pageUrl)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                    )
                    .header("Accept-Language", "en-US,en;q=0.9,pt;q=0.8")
                    .build()
                client.newCall(request).execute().use { response ->
                    val html = response.body?.string().orEmpty()
                    val foundUrls = linkedSetOf<String>()
                    val mediaRegex = Regex("""https?://[^\s"'<>]+?\.(mp4|m3u8|webm|mov)(\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
                    mediaRegex.findAll(html).forEach { match ->
                        foundUrls.add(match.value.replace("\\/", "/"))
                    }
                    val ogVideoRegex = Regex("""<meta[^>]+property=["']og:video(?::url)?["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    ogVideoRegex.findAll(html).forEach { match ->
                        val url = match.groupValues.getOrNull(1)
                        if (!url.isNullOrBlank()) foundUrls.add(url)
                    }

                    val titleRegex = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    val pageTitle = titleRegex.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                        .ifBlank { Uri.parse(pageUrl).host ?: "Stream Extraído" }

                    val results = foundUrls.mapIndexed { idx, stream ->
                        DetectedVideoMedia(
                            streamUrl = stream,
                            title = if (foundUrls.size > 1) "$pageTitle #${idx + 1}" else pageTitle,
                            pageUrl = pageUrl,
                            mimeType = if (stream.contains(".m3u8", true)) "application/x-mpegURL" else "video/mp4",
                            referer = pageUrl
                        )
                    }
                    Result.success(results)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun exportToPublicDownloads(video: DownloadedVideoEntity): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                val sourceFile = File(video.localFilePath)
                if (!sourceFile.exists()) {
                    return@withContext Result.failure(IllegalStateException("Arquivo local não encontrado."))
                }
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Video.Media.MIME_TYPE, video.mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AegisX")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("Não foi possível criar destino no MediaStore."))

                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                Result.success(uri)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    companion object {
        private const val TAG = "VideoDownloader"
    }
}
