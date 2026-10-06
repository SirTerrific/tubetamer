package com.sirterrific.tubetamer.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerUrl {
    /**
     * Turn what the parent typed ("192.168.1.10:8080", "http://nas/tubetamer") into a
     * base URL ending with "/", or null if it is not a usable address.
     * Defaults to http:// because the server is normally on the home network.
     */
    fun normalize(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
            s = "http://$s"
        }
        val url = s.toHttpUrlOrNull() ?: return null
        val clean = url.newBuilder().query(null).fragment(null).build().toString()
        return if (clean.endsWith("/")) clean else "$clean/"
    }
}
