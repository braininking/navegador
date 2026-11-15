package com.example.data.remote

import android.util.Log
import com.example.data.local.AegisDao
import com.example.data.local.TranslationCacheEntity
import com.example.security.VaultCryptoManager
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

data class SupportedLanguage(
    val code: String,
    val label: String,
    val nativeLabel: String
)

data class PageTranslationResult(
    val detectedSourceLanguage: String,
    val translatedBlocks: List<String>,
    val fromOfflineCache: Boolean
)

data class GeneratedExtensionDraft(
    val name: String,
    val description: String,
    val category: String,
    val matchPattern: String,
    val jsCode: String,
    val cssCode: String
)

class TranslationAiRepository(private val dao: AegisDao) {

    private val translationSchema = Schema.obj(
        properties = mapOf(
            "sourceLanguage" to Schema.string(description = "Detected ISO-639-1 language code of input text, e.g. en, es, pt, fr"),
            "isAlreadyTargetLanguage" to Schema.boolean(description = "True if the text is already predominantly in the target language"),
            "translations" to Schema.array(
                items = Schema.string(),
                description = "Translated strings in the exact same order and count as the input array"
            )
        )
    )

    private val extensionSchema = Schema.obj(
        properties = mapOf(
            "name" to Schema.string(description = "Short extension name"),
            "description" to Schema.string(description = "Clear one-line description in Portuguese"),
            "category" to Schema.enumeration(
                values = listOf("adblock", "appearance", "desktop", "privacy", "media", "custom"),
                description = "Extension category"
            ),
            "matchPattern" to Schema.string(description = "URL wildcard pattern like *://*/*"),
            "jsCode" to Schema.string(description = "Safe self-contained JavaScript IIFE to run on the page"),
            "cssCode" to Schema.string(description = "Optional CSS rules")
        )
    )

    private val translationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
                temperature = 0.2f
            }
        )
    }

    private val assistantModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    private val extensionGenModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = extensionSchema
            }
        )
    }

    suspend fun translatePageSegments(
        segments: List<String>,
        targetLanguageCode: String,
        targetLanguageLabel: String,
        htmlLangHint: String?
    ): Result<PageTranslationResult> {
        if (segments.isEmpty()) {
            return Result.success(PageTranslationResult(targetLanguageCode, emptyList(), false))
        }

        val joined = segments.joinToString("\u0001")
        val sourceHash = VaultCryptoManager.sha256Hex(joined)

        // 1. Check encrypted offline Room cache first for instant agility & offline capability
        val cached = dao.getCachedTranslation(sourceHash, targetLanguageCode)
        if (cached != null) {
            val decrypted = VaultCryptoManager.decrypt(cached.translatedPayloadEncrypted)
            if (decrypted.isNotBlank()) {
                val restored = runCatching {
                    val arr = JSONArray(decrypted)
                    (0 until arr.length()).map { arr.getString(it) }
                }.getOrNull()
                if (restored != null && restored.size == segments.size) {
                    return Result.success(
                        PageTranslationResult(
                            detectedSourceLanguage = cached.sourceLanguage,
                            translatedBlocks = restored,
                            fromOfflineCache = true
                        )
                    )
                }
            }
        }

        // 2. Check fast offline heuristic if already in target language
        val heuristicLang = detectLanguageHeuristic(segments.take(6).joinToString(" "), htmlLangHint)
        if (heuristicLang == targetLanguageCode) {
            return Result.success(
                PageTranslationResult(
                    detectedSourceLanguage = heuristicLang,
                    translatedBlocks = segments,
                    fromOfflineCache = true
                )
            )
        }

        // 3. Call Firebase AI Logic Gemini model for high-accuracy automatic translation
        return try {
            val inputJson = JSONArray(segments).toString()
            val prompt = """
                Detect the source language of the following webpage UI/content text segments and translate each item accurately into $targetLanguageLabel (ISO code: $targetLanguageCode).
                Keep proper nouns, brand names, and URLs intact. Return an array with the exact same length (${segments.size}).
                Input segments JSON:
                $inputJson
            """.trimIndent()

            val response = withTimeout(45_000L) {
                translationModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("Resposta vazia do tradutor Gemini.")
            val json = JSONObject(raw)
            val detected = json.optString("sourceLanguage", heuristicLang).lowercase().take(5)
            val isSame = json.optBoolean("isAlreadyTargetLanguage", false)
            val arr = json.getJSONArray("translations")
            val translatedList = (0 until arr.length()).map { arr.getString(it) }

            val finalBlocks = if (isSame || translatedList.size != segments.size) {
                if (translatedList.size == segments.size) translatedList else segments
            } else {
                // Save encrypted to local Room cache for offline speed on revisit
                val encoded = JSONArray(translatedList).toString()
                dao.insertTranslationCache(
                    TranslationCacheEntity(
                        sourceHash = sourceHash,
                        targetLanguage = targetLanguageCode,
                        sourceLanguage = detected,
                        translatedPayloadEncrypted = VaultCryptoManager.encrypt(encoded)
                    )
                )
                translatedList
            }

            Result.success(
                PageTranslationResult(
                    detectedSourceLanguage = detected,
                    translatedBlocks = finalBlocks,
                    fromOfflineCache = false
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Translation timed out", e)
            Result.failure(IllegalStateException("Tradução expirou. Usando modo offline.", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            // Offline fallback using embedded phrase dictionary so system stays responsive offline
            val offlineTranslated = segments.map { offlinePhraseFallback(it, targetLanguageCode) }
            if (offlineTranslated != segments) {
                Result.success(
                    PageTranslationResult(
                        detectedSourceLanguage = heuristicLang,
                        translatedBlocks = offlineTranslated,
                        fromOfflineCache = true
                    )
                )
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun translateSingleText(
        text: String,
        targetLanguageCode: String,
        targetLanguageLabel: String
    ): Result<String> {
        if (text.isBlank()) return Result.success("")
        val hash = VaultCryptoManager.sha256Hex("single:$text")
        val cached = dao.getCachedTranslation(hash, targetLanguageCode)
        if (cached != null) {
            val decrypted = VaultCryptoManager.decrypt(cached.translatedPayloadEncrypted)
            if (decrypted.isNotBlank()) return Result.success(decrypted)
        }
        return try {
            val response = withTimeout(40_000L) {
                assistantModel.generateContent(
                    "Translate the following text cleanly into $targetLanguageLabel ($targetLanguageCode). Return only the translated text:\n\n$text"
                )
            }
            val translated = response.text?.trim()
                ?: throw IllegalStateException("Tradução retornou vazia.")
            dao.insertTranslationCache(
                TranslationCacheEntity(
                    sourceHash = hash,
                    targetLanguage = targetLanguageCode,
                    sourceLanguage = detectLanguageHeuristic(text, null),
                    translatedPayloadEncrypted = VaultCryptoManager.encrypt(translated)
                )
            )
            Result.success(translated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Single text translation failed", e)
            val fallback = offlinePhraseFallback(text, targetLanguageCode)
            if (fallback != text) Result.success(fallback) else Result.failure(e)
        }
    }

    suspend fun summarizePageWithPrivacyAudit(
        pageTitle: String,
        pageUrl: String,
        pageText: String,
        targetLanguageLabel: String
    ): Result<String> {
        return try {
            val truncated = pageText.take(6000)
            val prompt = """
                Você é o analisador de privacidade e síntese do navegador AegisX.
                Responda em $targetLanguageLabel de forma clara e objetiva com:
                1. Resumo principal da página (3-4 tópicos).
                2. Avaliação rápida de privacidade/confiabilidade do conteúdo e links.
                URL: $pageUrl
                Título: $pageTitle
                Conteúdo:
                $truncated
            """.trimIndent()
            val response = withTimeout(50_000L) {
                assistantModel.generateContent(prompt)
            }
            val text = response.text?.trim() ?: throw IllegalStateException("Resumo vazio.")
            Result.success(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Page summary failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateCustomExtension(userPrompt: String): Result<GeneratedExtensionDraft> {
        return try {
            val prompt = """
                Generate a clean, safe, self-contained browser userscript extension (JavaScript IIFE + optional CSS) for an Android Chromium WebView browser based on this user request:
                "$userPrompt"
                Ensure the JavaScript runs safely without external network fetches or eval().
            """.trimIndent()
            val response = withTimeout(50_000L) {
                extensionGenModel.generateContent(prompt)
            }
            val json = JSONObject(response.text ?: throw IllegalStateException("Resposta vazia."))
            Result.success(
                GeneratedExtensionDraft(
                    name = json.getString("name"),
                    description = json.getString("description"),
                    category = json.getString("category"),
                    matchPattern = json.getString("matchPattern"),
                    jsCode = json.getString("jsCode"),
                    cssCode = json.optString("cssCode", "")
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Extension generation failed", e)
            Result.failure(e)
        }
    }

    fun detectLanguageHeuristic(sample: String, htmlLangHint: String?): String {
        val hint = htmlLangHint?.trim()?.lowercase()?.substringBefore("-")?.substringBefore("_")
        if (!hint.isNullOrBlank() && hint.length == 2 && SUPPORTED_LANGUAGES.any { it.code == hint }) {
            // Confirm with text markers when possible
            val lower = " ${sample.lowercase()} "
            if (hint == "en" && (lower.contains(" the ") || lower.contains(" and ") || lower.contains(" with "))) return "en"
            if (hint == "pt" && (lower.contains(" que ") || lower.contains(" para ") || lower.contains(" com "))) return "pt"
            if (hint == "es" && (lower.contains(" el ") || lower.contains(" los ") || lower.contains(" para "))) return "es"
            return hint
        }
        val lower = " ${sample.lowercase()} "
        val ptScore = listOf(" você ", " não ", " para ", " com ", " uma ", " mais ", " sobre ", " página ", " entrar ").count { lower.contains(it) }
        val enScore = listOf(" the ", " and ", " with ", " from ", " this ", " search ", " home ", " privacy ", " sign in ").count { lower.contains(it) }
        val esScore = listOf(" el ", " los ", " las ", " para ", " con ", " una ", " más ", " iniciar ", " buscar ").count { lower.contains(it) }
        val frScore = listOf(" le ", " les ", " avec ", " pour ", " dans ", " une ", " sur ", " rechercher ").count { lower.contains(it) }
        val deScore = listOf(" der ", " die ", " das ", " und ", " mit ", " für ", " nicht ", " suchen ").count { lower.contains(it) }

        val best = mapOf(
            "pt" to ptScore,
            "en" to enScore,
            "es" to esScore,
            "fr" to frScore,
            "de" to deScore
        ).maxByOrNull { it.value }

        return if (best != null && best.value > 0) best.key else "en"
    }

    private fun offlinePhraseFallback(text: String, targetLang: String): String {
        if (targetLang != "pt") return text
        val dictionary = mapOf(
            "Sign in" to "Entrar",
            "Search" to "Pesquisar",
            "Settings" to "Configurações",
            "Privacy Policy" to "Política de Privacidade",
            "Download" to "Baixar",
            "Home" to "Início",
            "News" to "Notícias",
            "Read more" to "Leia mais",
            "Learn more" to "Saiba mais",
            "Accept all" to "Aceitar tudo",
            "Subscribe" to "Inscrever-se",
            "Videos" to "Vídeos",
            "Images" to "Imagens",
            "About" to "Sobre"
        )
        var result = text
        dictionary.forEach { (en, pt) ->
            if (result.equals(en, ignoreCase = true)) result = pt
        }
        return result
    }

    companion object {
        private const val TAG = "TranslationAiRepo"

        val SUPPORTED_LANGUAGES = listOf(
            SupportedLanguage("pt", "Português", "Português (Brasil / PT)"),
            SupportedLanguage("en", "Inglês", "English"),
            SupportedLanguage("es", "Espanhol", "Español"),
            SupportedLanguage("fr", "Francês", "Français"),
            SupportedLanguage("de", "Alemão", "Deutsch"),
            SupportedLanguage("it", "Italiano", "Italiano"),
            SupportedLanguage("ja", "Japonês", "日本語"),
            SupportedLanguage("ko", "Coreano", "한국어"),
            SupportedLanguage("zh", "Chinês", "中文")
        )
    }
}
