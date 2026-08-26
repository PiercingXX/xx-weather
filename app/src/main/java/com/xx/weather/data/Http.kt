package com.xx.weather.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Minimal blocking HTTP GET helper built on HttpURLConnection (zero extra deps). */
internal object Http {

    fun get(url: String, userAgent: String? = null, timeoutMs: Int = 12000): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            // Split the caller budget across connect+read so a slow handshake
            // plus a slow first byte cannot run ~2x timeoutMs.
            val total = timeoutMs.coerceAtLeast(500)
            val connectMs = (total / 3).coerceAtLeast(250)
            conn.connectTimeout = connectMs
            conn.readTimeout = (total - connectMs).coerceAtLeast(250)
            conn.setRequestProperty("Accept-Encoding", "gzip")
            if (userAgent != null) conn.setRequestProperty("User-Agent", userAgent)
            val code = conn.responseCode
            val raw = if (code in 200..299) conn.inputStream else (conn.errorStream ?: throw IOException("HTTP $code for $url"))
            val body = (if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) GZIPInputStream(raw) else raw)
                .use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
            if (code !in 200..299) throw IOException("HTTP $code: ${body.take(200)}")
            return body
        } finally {
            conn.disconnect()
        }
    }
}
