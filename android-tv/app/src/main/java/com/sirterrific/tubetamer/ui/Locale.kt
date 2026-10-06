package com.sirterrific.tubetamer.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Languages the app has strings for. Others fall back to the device language. */
private val SUPPORTED = setOf("en", "fr", "nb")

/**
 * Maps the server's locale ("fr", "fr-CA", "no", "nn"...) to one the app supports,
 * same rules as the server's normalize_locale. Null = keep the device language.
 */
internal fun appLanguage(serverLocale: String?): String? {
    val v = serverLocale?.trim()?.lowercase()?.replace('_', '-').orEmpty()
    if (v.isEmpty()) return null
    val lang = when {
        v == "no" || v == "nn" || v.startsWith("no-") || v.startsWith("nn-") -> "nb"
        else -> v.substringBefore('-')
    }
    return lang.takeIf { it in SUPPORTED }
}

/**
 * Shows [content] in the server's language, so the menus match the video titles
 * the server sends. Without a known server language the device language applies.
 */
@Composable
fun ServerLanguage(serverLocale: String?, content: @Composable () -> Unit) {
    val lang = appLanguage(serverLocale)
    if (lang == null) {
        content()
        return
    }
    val base = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val (context, config) = remember(base, baseConfig, lang) {
        val c = Configuration(baseConfig).apply { setLocale(Locale.forLanguageTag(lang)) }
        base.createConfigurationContext(c) to c
    }
    CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config) {
        content()
    }
}
