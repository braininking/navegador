package com.example.data.remote

import android.content.Context
import android.os.Build
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AegisSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Usuário deve estar autenticado com Google antes de sincronizar.")
    }

    private fun defaultDeviceName(): String {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
        val manufacturer = Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: ""
        val combined = "$manufacturer $model".trim()
        return combined.take(80).ifBlank { "Android Device" }
    }

    fun observePreferences(): Flow<UserPreferences?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("profile").document(PREFS_DOC_ID)
        emitAll(
            docRef.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserPreferences::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun getPreferencesOnce(): UserPreferences? {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("profile").document(PREFS_DOC_ID)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(
                    UserPreferences::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun saveOrUpdatePreferences(prefs: UserPreferences) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("profile").document(PREFS_DOC_ID)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "preferredLanguage" to prefs.preferredLanguage.take(10),
                    "preferredLanguageLabel" to prefs.preferredLanguageLabel.take(40),
                    "autoTranslateEnabled" to prefs.autoTranslateEnabled,
                    "onboardingCompleted" to prefs.onboardingCompleted,
                    "adBlockEnabled" to prefs.adBlockEnabled,
                    "strictTrackerBlock" to prefs.strictTrackerBlock,
                    "vpnAutoConnect" to prefs.vpnAutoConnect,
                    "vpnRegion" to prefs.vpnRegion.take(40),
                    "desktopModeDefault" to prefs.desktopModeDefault,
                    "darkThemeEnabled" to prefs.darkThemeEnabled,
                    "searchEngine" to prefs.searchEngine.take(30),
                    "deviceName" to prefs.deviceName.ifBlank { defaultDeviceName() }.take(80),
                    "adsBlockedTotal" to prefs.adsBlockedTotal.coerceAtLeast(0L),
                    "trackersBlockedTotal" to prefs.trackersBlockedTotal.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                prefs.displayName?.takeIf { it.isNotBlank() }?.let { updates["displayName"] = it.take(100) }
                prefs.email?.takeIf { it.isNotBlank() }?.let { updates["email"] = it.take(200) }
                docRef.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "preferredLanguage" to prefs.preferredLanguage.take(10),
                    "preferredLanguageLabel" to prefs.preferredLanguageLabel.take(40),
                    "autoTranslateEnabled" to prefs.autoTranslateEnabled,
                    "onboardingCompleted" to prefs.onboardingCompleted,
                    "adBlockEnabled" to prefs.adBlockEnabled,
                    "strictTrackerBlock" to prefs.strictTrackerBlock,
                    "vpnAutoConnect" to prefs.vpnAutoConnect,
                    "vpnRegion" to prefs.vpnRegion.take(40),
                    "desktopModeDefault" to prefs.desktopModeDefault,
                    "darkThemeEnabled" to prefs.darkThemeEnabled,
                    "searchEngine" to prefs.searchEngine.take(30),
                    "deviceName" to prefs.deviceName.ifBlank { defaultDeviceName() }.take(80),
                    "adsBlockedTotal" to prefs.adsBlockedTotal.coerceAtLeast(0L),
                    "trackersBlockedTotal" to prefs.trackersBlockedTotal.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                (prefs.displayName ?: auth.currentUser?.displayName)?.takeIf { it.isNotBlank() }?.let {
                    payload["displayName"] = it.take(100)
                }
                (prefs.email ?: auth.currentUser?.email)?.takeIf { it.isNotBlank() }?.let {
                    payload["email"] = it.take(200)
                }
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeBookmarks(): Flow<List<SyncedBookmark>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("bookmarks")
        emitAll(
            colRef.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.toObjects(
                        SyncedBookmark::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun addBookmark(
        title: String,
        url: String,
        folder: String = "Favoritos",
        bookmarkId: String = ""
    ): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("bookmarks")
        val safeId = bookmarkId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val docRef = if (safeId.isNotBlank()) colRef.document(safeId) else colRef.document()
        try {
            val existing = if (safeId.isNotBlank()) docRef.get().await() else null
            if (existing != null && existing.exists()) {
                docRef.update(
                    mapOf(
                        "title" to title.trim().ifBlank { url }.take(200),
                        "url" to url.trim().take(2000),
                        "folder" to folder.trim().ifBlank { "Favoritos" }.take(60),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to title.trim().ifBlank { url }.take(200),
                    "url" to url.trim().take(2000),
                    "folder" to folder.trim().ifBlank { "Favoritos" }.take(60),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteBookmark(bookmarkId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("bookmarks").document(bookmarkId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeExtensions(): Flow<List<BrowserExtension>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("extensions")
        emitAll(
            colRef.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.toObjects(
                        BrowserExtension::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveExtension(extension: BrowserExtension): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("extensions")
        val docRef = if (extension.id.isNotBlank()) colRef.document(extension.id) else colRef.document()
        val safeCategory = if (extension.category in VALID_CATEGORIES) extension.category else "custom"
        val safeRunAt = if (extension.runAt in VALID_RUN_AT) extension.runAt else "document_end"

        try {
            val existing = if (extension.id.isNotBlank()) docRef.get().await() else null
            if (existing != null && existing.exists()) {
                val updates = mapOf(
                    "name" to extension.name.trim().ifBlank { "Plugin" }.take(100),
                    "description" to extension.description.trim().take(500),
                    "version" to extension.version.trim().ifBlank { "1.0.0" }.take(20),
                    "author" to extension.author.trim().ifBlank { "Comunidade" }.take(80),
                    "category" to safeCategory,
                    "matchPattern" to extension.matchPattern.trim().ifBlank { "*://*/*" }.take(200),
                    "jsCode" to extension.jsCode.take(30000),
                    "cssCode" to extension.cssCode.take(20000),
                    "enabled" to extension.enabled,
                    "runAt" to safeRunAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "name" to extension.name.trim().ifBlank { "Plugin" }.take(100),
                    "description" to extension.description.trim().take(500),
                    "version" to extension.version.trim().ifBlank { "1.0.0" }.take(20),
                    "author" to extension.author.trim().ifBlank { "Comunidade" }.take(80),
                    "category" to safeCategory,
                    "matchPattern" to extension.matchPattern.trim().ifBlank { "*://*/*" }.take(200),
                    "jsCode" to extension.jsCode.take(30000),
                    "cssCode" to extension.cssCode.take(20000),
                    "enabled" to extension.enabled,
                    "isBuiltIn" to extension.isBuiltIn,
                    "runAt" to safeRunAt,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteExtension(extensionId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("extensions").document(extensionId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeSyncedTabs(): Flow<List<SyncedTab>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("synced_tabs")
        emitAll(
            colRef.snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.toObjects(
                        SyncedTab::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertSyncedTab(tabId: String, title: String, url: String, isIncognito: Boolean = false) {
        val uid = requireUserId()
        val safeId = tabId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "tab_main" }
        val docRef = db.collection("users").document(uid).collection("synced_tabs").document(safeId)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "title" to title.trim().ifBlank { url }.take(200),
                        "url" to url.trim().take(2000),
                        "deviceName" to defaultDeviceName(),
                        "isIncognito" to isIncognito,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "title" to title.trim().ifBlank { url }.take(200),
                        "url" to url.trim().take(2000),
                        "deviceName" to defaultDeviceName(),
                        "isIncognito" to isIncognito,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteSyncedTab(tabId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("synced_tabs").document(tabId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    companion object {
        const val PREFS_DOC_ID = "preferences"
        private val VALID_CATEGORIES = setOf("adblock", "appearance", "desktop", "privacy", "media", "custom")
        private val VALID_RUN_AT = setOf("document_start", "document_end")
    }
}
