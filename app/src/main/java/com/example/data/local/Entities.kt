package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "offline_pages")
data class OfflinePageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val htmlContentEncrypted: String,
    val textSnippetEncrypted: String,
    val detectedLanguage: String = "auto",
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "translation_cache",
    indices = [Index(value = ["sourceHash", "targetLanguage"], unique = true)]
)
data class TranslationCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceHash: String,
    val targetLanguage: String,
    val sourceLanguage: String,
    val translatedPayloadEncrypted: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_videos")
data class DownloadedVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceUrl: String,
    val pageUrl: String,
    val localFilePath: String,
    val mimeType: String = "video/mp4",
    val fileSizeBytes: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history_entries")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleEncrypted: String,
    val urlEncrypted: String,
    val host: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_extensions")
data class LocalExtensionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val category: String,
    val matchPattern: String,
    val jsCode: String,
    val cssCode: String,
    val enabled: Boolean,
    val isBuiltIn: Boolean,
    val runAt: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_bookmarks")
data class LocalBookmarkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val folder: String = "Favoritos",
    val updatedAt: Long = System.currentTimeMillis()
)

