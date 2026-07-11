package com.quellkern.nachweis.presentation

import java.net.HttpURLConnection
import java.net.URL

/**
 * A [StatusListFetcher] that performs a plain HTTPS GET of the public status-list artifact. It
 * sends no verifier-identifying parameters, cookies, or auth — the URL is a public, pre-signed
 * artifact — and refuses non-HTTPS URLs so a status list is never fetched over cleartext. Any
 * transport failure returns null, which the refresher treats as "leave the prior entry to age
 * out"; the status token's own signature and `sub` are what establish trust, checked afterwards
 * by [StatusListVerifier], so this fetcher is deliberately dumb about content.
 */
class HttpStatusListFetcher(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 10_000,
    private val maxBytes: Int = 512 * 1024,
) : StatusListFetcher {

    override fun fetch(uri: String): String? {
        val url = try {
            URL(uri)
        } catch (_: Exception) {
            return null
        }
        if (!url.protocol.equals("https", ignoreCase = true)) return null

        var connection: HttpURLConnection? = null
        return try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/statuslist+jwt, application/jwt")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { stream ->
                val bytes = stream.readNBytes(maxBytes)
                if (bytes.isEmpty()) null else String(bytes, Charsets.UTF_8).trim()
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
