package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        OfflinePageEntity::class,
        TranslationCacheEntity::class,
        DownloadedVideoEntity::class,
        HistoryEntity::class,
        LocalExtensionEntity::class,
        LocalBookmarkEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AegisDatabase : RoomDatabase() {
    abstract fun aegisDao(): AegisDao

    companion object {
        @Volatile
        private var INSTANCE: AegisDatabase? = null

        fun getInstance(context: Context): AegisDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AegisDatabase::class.java,
                    "aegisx_encrypted_vault.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
