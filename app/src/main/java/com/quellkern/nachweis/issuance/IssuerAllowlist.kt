package com.quellkern.nachweis.issuance

/**
 * The set of credential issuers this build is permitted to accept offers from. An offer
 * whose credential-issuer identifier is not on the list is rejected before any network
 * exchange that would disclose wallet state to it.
 *
 * The allowlist is derived from the active flavor's configured issuer plus an optional,
 * developer-supplied local override (never a committed default). Matching is by URL origin
 * (scheme + host + port), so a path or query on the offer's issuer identifier cannot smuggle
 * in an unlisted host.
 */
class IssuerAllowlist(origins: Collection<String>) {

    private val allowed: Set<String> = origins.mapNotNull(::originOf).toSet()

    /** True when [issuerIdentifier]'s origin is on the allowlist. */
    fun isAllowed(issuerIdentifier: String?): Boolean {
        val origin = originOf(issuerIdentifier) ?: return false
        return origin in allowed
    }

    /** The normalized origins on the list; exposed for diagnostics and tests. */
    fun origins(): Set<String> = allowed

    companion object {
        /**
         * Reduce a URL to its origin: lowercased scheme + host + explicit non-default port.
         * Returns null for blank or non-hierarchical input so it can never match.
         */
        fun originOf(url: String?): String? {
            if (url.isNullOrBlank()) return null
            return try {
                val uri = java.net.URI(url.trim())
                val scheme = uri.scheme?.lowercase() ?: return null
                val host = uri.host?.lowercase() ?: return null
                val port = uri.port
                val defaultPort = when (scheme) {
                    "https" -> 443
                    "http" -> 80
                    else -> -1
                }
                if (port == -1 || port == defaultPort) "$scheme://$host" else "$scheme://$host:$port"
            } catch (_: java.net.URISyntaxException) {
                null
            }
        }
    }
}
