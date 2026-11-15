package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AegisDao {
    // Offline Pages
    @Query("SELECT * FROM offline_pages ORDER BY savedAt DESC")
    fun observeOfflinePages(): Flow<List<OfflinePageEntity>>

    @Query("SELECT * FROM offline_pages WHERE url = :url LIMIT 1")
    suspend fun getOfflinePageByUrl(url: String): OfflinePageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfflinePage(page: OfflinePageEntity): Long

    @Query("DELETE FROM offline_pages WHERE id = :id")
    suspend fun deleteOfflinePage(id: Long)

    // Translation Cache (Offline Agility)
    @Query("SELECT * FROM translation_cache WHERE sourceHash = :sourceHash AND targetLanguage = :targetLang LIMIT 1")
    suspend fun getCachedTranslation(sourceHash: String, targetLang: String): TranslationCacheEntity?

    @Query("SELECT COUNT(*) FROM translation_cache")
    fun observeTranslationCacheCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslationCache(entry: TranslationCacheEntity)

    @Query("DELETE FROM translation_cache")
    suspend fun clearTranslationCache()

    // Downloaded Videos
    @Query("SELECT * FROM downloaded_videos ORDER BY downloadedAt DESC")
    fun observeDownloadedVideos(): Flow<List<DownloadedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedVideo(video: DownloadedVideoEntity): Long

    @Query("DELETE FROM downloaded_videos WHERE id = :id")
    suspend fun deleteDownloadedVideo(id: Long)

    // Encrypted Local History
    @Query("SELECT * FROM history_entries ORDER BY visitedAt DESC LIMIT 100")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntity)

    @Query("DELETE FROM history_entries")
    suspend fun clearHistory()

    // Local Extensions (works without login, syncs when signed in)
    @Query("SELECT * FROM local_extensions ORDER BY isBuiltIn DESC, name COLLATE NOCASE ASC")
    fun observeLocalExtensions(): Flow<List<LocalExtensionEntity>>

    @Query("SELECT * FROM local_extensions")
    suspend fun getLocalExtensionsOnce(): List<LocalExtensionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalExtension(extension: LocalExtensionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalExtensions(extensions: List<LocalExtensionEntity>)

    @Query("DELETE FROM local_extensions WHERE id = :id")
    suspend fun deleteLocalExtension(id: String)

    // Local Bookmarks (works without login, syncs when signed in)
    @Query("SELECT * FROM local_bookmarks ORDER BY updatedAt DESC")
    fun observeLocalBookmarks(): Flow<List<LocalBookmarkEntity>>

    @Query("SELECT * FROM local_bookmarks")
    suspend fun getLocalBookmarksOnce(): List<LocalBookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalBookmark(bookmark: LocalBookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalBookmarks(bookmarks: List<LocalBookmarkEntity>)

    @Query("DELETE FROM local_bookmarks WHERE id = :id")
    suspend fun deleteLocalBookmark(id: String)
}
