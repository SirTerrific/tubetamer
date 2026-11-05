package com.sirterrific.tubetamer.ui

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Server paths like /thumb/abc resolve against the base URL (which may carry a sub-path). */
fun resolve(baseUrl: String, path: String): String? {
    if (path.isEmpty()) return null
    val base = baseUrl.toHttpUrlOrNull() ?: return null
    return base.resolve(path.removePrefix("/"))?.toString()
}

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun progressFraction(progress: Int, duration: Int): Float =
    if (duration <= 0 || progress <= 0) 0f else (progress.toFloat() / duration).coerceIn(0f, 1f)
