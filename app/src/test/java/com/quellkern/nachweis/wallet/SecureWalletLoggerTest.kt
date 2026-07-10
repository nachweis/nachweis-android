package com.quellkern.nachweis.wallet

import eu.europa.ec.eudi.wallet.logging.Logger
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Redaction contract: the logger must never emit credential material. wallet-core records
 * can carry disclosed claims in [Logger.Record.message] and payloads in
 * [Logger.Record.thrown]; the formatter must drop both.
 */
class SecureWalletLoggerTest {

    private val sensitive = "PID given_name=Ada family_name=Lovelace token=eyJhbGciOi"

    private fun record(level: Int = Logger.LEVEL_ERROR) = Logger.Record(
        level,
        Instant.EPOCH,
        sensitive,
        IllegalStateException("secret cause $sensitive"),
        "eu.europa.ec.eudi.wallet.SomeManager",
        "issueDocument",
    )

    @Test
    fun release_emitsNothing() {
        assertNull(SecureWalletLogger.format(record(), debuggable = false))
    }

    @Test
    fun debug_neverContainsMessageOrThrowable() {
        val line = SecureWalletLogger.format(record(), debuggable = true)
        requireNotNull(line)
        assertFalse("message body must not appear", line.contains("Ada"))
        assertFalse("token must not appear", line.contains("eyJhbGciOi"))
        assertFalse("throwable text must not appear", line.contains("secret cause"))
    }

    @Test
    fun debug_keepsLevelAndSourceLocation() {
        val line = SecureWalletLogger.format(record(Logger.LEVEL_ERROR), debuggable = true)
        requireNotNull(line)
        assertTrue(line.contains("ERROR"))
        assertTrue(line.contains("SomeManager"))
        assertTrue(line.contains("issueDocument"))
    }

    @Test
    fun debug_signalsSuppressedErrorWithoutDetail() {
        val line = SecureWalletLogger.format(record(), debuggable = true)
        requireNotNull(line)
        assertTrue("presence of an error is signalled", line.contains("error suppressed"))
    }
}
