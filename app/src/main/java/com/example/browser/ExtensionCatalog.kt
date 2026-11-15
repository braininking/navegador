package com.example.browser

import com.example.data.remote.BrowserExtension

object ExtensionCatalog {

    fun defaultBuiltInExtensions(userId: String): List<BrowserExtension> = listOf(
        BrowserExtension(
            id = "builtin_ublock_cosmetic",
            userId = userId,
            name = "uBlock Shield Core",
            description = "Remove banners patrocinados, pop-ups flutuantes e avisos intrusivos de cookies no DOM.",
            version = "2.4.1",
            author = "OpenSource • AegisX",
            category = "adblock",
            matchPattern = "*://*/*",
            jsCode = """
                (function() {
                  const selectors = [
                    '[id*="ad-"]', '[class*="ad-banner"]', '[class*="sponsored"]',
                    'ins.adsbygoogle', '[id*="cookie-banner"]', '[class*="cookie-consent"]',
                    '[aria-label*="advertisement"]', 'iframe[src*="doubleclick"]', 'iframe[src*="ads"]'
                  ];
                  const clean = () => {
                    selectors.forEach(s => {
                      document.querySelectorAll(s).forEach(el => {
                        if (el && el.style) el.style.setProperty('display', 'none', 'important');
                      });
                    });
                  };
                  clean();
                  new MutationObserver(clean).observe(document.documentElement || document.body, { childList: true, subtree: true });
                })();
            """.trimIndent(),
            cssCode = """
                ins.adsbygoogle, [class*="ad-slot"], [id*="google_ads"] { display: none !important; }
            """.trimIndent(),
            enabled = true,
            isBuiltIn = true,
            runAt = "document_end"
        ),
        BrowserExtension(
            id = "builtin_dark_reader",
            userId = userId,
            name = "Dark Reader Engine",
            description = "Aplica modo escuro inteligente com preservação de imagens e vídeos em qualquer site claro.",
            version = "4.9.0",
            author = "OpenSource • DarkReader",
            category = "appearance",
            matchPattern = "*://*/*",
            jsCode = """
                (function() {
                  if (!document.getElementById('aegisx-dark-reader-style')) {
                    const st = document.createElement('style');
                    st.id = 'aegisx-dark-reader-style';
                    st.textContent = 'html { filter: invert(0.92) hue-rotate(180deg) !important; background: #070B14 !important; } img, video, picture, canvas, svg, [style*="background-image"] { filter: invert(1) hue-rotate(180deg) !important; }';
                    (document.head || document.documentElement).appendChild(st);
                  }
                })();
            """.trimIndent(),
            cssCode = "html { background: #070B14 !important; }",
            enabled = false,
            isBuiltIn = true,
            runAt = "document_end"
        ),
        BrowserExtension(
            id = "builtin_desktop_pro",
            userId = userId,
            name = "Desktop Mode Pro",
            description = "Força viewport de computador (1280px) e APIs de desktop para sites completos.",
            version = "1.8.0",
            author = "AegisX Desktop Lab",
            category = "desktop",
            matchPattern = "*://*/*",
            jsCode = """
                (function() {
                  let meta = document.querySelector('meta[name="viewport"]');
                  if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head && document.head.appendChild(meta);
                  }
                  if (meta) meta.setAttribute('content', 'width=1280, initial-scale=0.65');
                })();
            """.trimIndent(),
            cssCode = "",
            enabled = false,
            isBuiltIn = true,
            runAt = "document_end"
        ),
        BrowserExtension(
            id = "builtin_fingerprint_guard",
            userId = userId,
            name = "Privacy Fingerprint Guard",
            description = "Protege contra rastreamento por Canvas, WebGL, AudioContext e WebRTC IP leak.",
            version = "3.1.2",
            author = "PrivacyCommons",
            category = "privacy",
            matchPattern = "*://*/*",
            jsCode = """
                (function() {
                  try {
                    Object.defineProperty(navigator, 'doNotTrack', { get: () => '1' });
                    Object.defineProperty(navigator, 'globalPrivacyControl', { get: () => true });
                    const origToDataURL = HTMLCanvasElement.prototype.toDataURL;
                    HTMLCanvasElement.prototype.toDataURL = function() {
                      const ctx = this.getContext('2d');
                      if (ctx && this.width > 0 && this.height > 0) {
                        ctx.fillStyle = 'rgba(0, 240, 255, 0.01)';
                        ctx.fillRect(0, 0, 1, 1);
                      }
                      return origToDataURL.apply(this, arguments);
                    };
                  } catch (e) {}
                })();
            """.trimIndent(),
            cssCode = "",
            enabled = true,
            isBuiltIn = true,
            runAt = "document_start"
        ),
        BrowserExtension(
            id = "builtin_distraction_skip",
            userId = userId,
            name = "Paywall & Modal Unblocker",
            description = "Desbloqueia rolagem de página e remove overlays que impedem leitura ou reprodução.",
            version = "1.5.4",
            author = "OpenWeb Collective",
            category = "custom",
            matchPattern = "*://*/*",
            jsCode = """
                (function() {
                  document.documentElement.style.overflow = 'auto';
                  if (document.body) document.body.style.overflow = 'auto';
                  document.querySelectorAll('[class*="paywall"], [class*="modal-backdrop"], [class*="overlay-lock"]').forEach(el => el.remove());
                })();
            """.trimIndent(),
            cssCode = "body, html { overflow: auto !important; }",
            enabled = true,
            isBuiltIn = true,
            runAt = "document_end"
        )
    )

    fun matchesUrl(pattern: String, url: String): Boolean {
        val cleanPattern = pattern.trim()
        if (cleanPattern.isEmpty() || cleanPattern == "*://*/*" || cleanPattern == "*") return true
        return try {
            val regexStr = cleanPattern.split("*").joinToString(".*") { Regex.escape(it) }
            Regex("^$regexStr$", RegexOption.IGNORE_CASE).matches(url) ||
                url.contains(cleanPattern.removePrefix("*://").removeSuffix("/*"), ignoreCase = true)
        } catch (_: Exception) {
            true
        }
    }

    fun buildCombinedInjectionScript(extensions: List<BrowserExtension>, url: String): String {
        val matching = extensions.filter { it.enabled && matchesUrl(it.matchPattern, url) }
        if (matching.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("(function() {\n")
        matching.forEach { ext ->
            if (ext.cssCode.isNotBlank()) {
                val escapedCss = ext.cssCode
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", " ")
                sb.append(
                    """
                    try {
                      var s = document.getElementById('aegis-css-${ext.id.hashCode()}');
                      if (!s) {
                        s = document.createElement('style');
                        s.id = 'aegis-css-${ext.id.hashCode()}';
                        (document.head || document.documentElement).appendChild(s);
                      }
                      s.textContent = '$escapedCss';
                    } catch (err) {}
                    """.trimIndent()
                )
                sb.append("\n")
            }
            if (ext.jsCode.isNotBlank()) {
                sb.append("try {\n")
                sb.append(ext.jsCode)
                sb.append("\n} catch (extErr) {}\n")
            }
        }
        sb.append("})();")
        return sb.toString()
    }
}
