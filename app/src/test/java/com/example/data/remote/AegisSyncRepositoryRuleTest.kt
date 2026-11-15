package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class AegisSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveAndObservePreferences_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = AegisSyncRepository(firestore)

        val prefs = UserPreferences(
            userId = uid,
            displayName = "Alice",
            email = ALICE_EMAIL,
            preferredLanguage = "pt",
            preferredLanguageLabel = "Português",
            autoTranslateEnabled = true,
            onboardingCompleted = true,
            adBlockEnabled = true,
            strictTrackerBlock = true,
            vpnAutoConnect = true,
            vpnRegion = "Zurich, CH",
            desktopModeDefault = false,
            darkThemeEnabled = true,
            searchEngine = "DuckDuckGo",
            deviceName = "Pixel Test",
            adsBlockedTotal = 15L,
            trackersBlockedTotal = 7L
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveOrUpdatePreferences(prefs)
        }

        val loaded = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePreferences().first { it != null }
        }
        assertNotNull(loaded)
        assertEquals("pt", loaded?.preferredLanguage)
        assertEquals(true, loaded?.onboardingCompleted)
    }

    @Test
    fun saveBookmarkAndExtension_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = AegisSyncRepository(firestore)
        val uniqueTitle = "DuckDuckGo-${UUID.randomUUID().toString().take(6)}"

        val bookmarkId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addBookmark(uniqueTitle, "https://duckduckgo.com", "Privacidade")
        }
        assertTrue(bookmarkId.isNotBlank())

        val bookmarks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeBookmarks().first { list -> list.any { it.id == bookmarkId } }
        }
        assertTrue(bookmarks.any { it.title == uniqueTitle })

        val extId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveExtension(
                BrowserExtension(
                    userId = uid,
                    name = "Test Dark CSS",
                    description = "Dark mode",
                    version = "1.0.0",
                    author = "Tester",
                    category = "appearance",
                    matchPattern = "*://*/*",
                    jsCode = "console.log('ok');",
                    cssCode = "body { background: #000; }",
                    enabled = true,
                    isBuiltIn = false,
                    runAt = "document_end"
                )
            )
        }
        assertTrue(extId.isNotBlank())

        val exts = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeExtensions().first { list -> list.any { it.id == extId } }
        }
        assertTrue(exts.any { it.id == extId && it.name == "Test Dark CSS" })
    }

    @Test
    fun crossUserRead_differentAuthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = AegisSyncRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addBookmark("Alice Secret", "https://example.org", "Vault")
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("bookmarks").get().await()
            }
            fail("Expected PERMISSION_DENIED for cross-user read")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    @Test
    fun unauthenticatedRead_signedOut_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("profile").document("preferences").get().await()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.aegisx@test.com"
        const val BOB_EMAIL = "bob.aegisx@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
