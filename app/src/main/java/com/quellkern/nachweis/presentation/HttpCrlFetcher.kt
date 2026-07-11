package com.quellkern.nachweis.presentation

import java.net.HttpURLConnection
import java.net.URL

/**
 * A [CrlFetcher] that performs a plain HTTPS GET of the public DER CRL. It sends no
 * verifier-identifying parameters, cookies, or auth — the URL is a public, pre-signed artifact —
 * and refuses non-HTTPS URLs so a CRL is never fetched over cleartext. Any transport failure
 * returns null, which the refresher treats as "leave the prior entry to age out"; the CRL's own
 * issuer signature is what establishes trust, checked afterwards by [WrpacCrlRefresher].
 */
class HttpCrlFetcher(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 10_000,
    private val maxBytes: Int = 1024 * 1024,
) : CrlFetcher {

    override fun fetch(uri: String): ByteArray? {
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
                setRequestProperty("Accept", "application/pkix-crl, application/octet-stream")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { it.readNBytes(maxBytes) }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
