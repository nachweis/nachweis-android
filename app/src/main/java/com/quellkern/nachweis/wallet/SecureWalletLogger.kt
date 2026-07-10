package com.quellkern.nachweis.wallet

import android.util.Log
import eu.europa.ec.eudi.wallet.logging.Logger

/**
 * wallet-core [Logger] that cannot leak credential material to logcat.
 *
 * wallet-core log records can carry document contents, disclosed claims, and request
 * payloads in their message and throwable. This logger therefore emits *only* metadata:
 * the level and the originating class/method. Message bodies and stack traces are never
 * written. In a non-debuggable (release) build it emits nothing at all.
 *
 * [format] is a pure function so the redaction contract is unit-testable without a device.
 */
class SecureWalletLogger(
    private val debuggable: Boolean,
    private val sink: (level: Int, line: String) -> Unit = ::androidSink,
) : Logger {

    override fun log(record: Logger.Record) {
        val line = format(record, debuggable) ?: return
        sink(record.level, line)
    }

    companion object {
        // Mirror wallet-core's Logger level constants so callers configure logging in one
        // vocabulary (see WalletSecurityPolicy / EudiWalletConfig.configureLogging).
        const val OFF: Int = Logger.OFF
        const val LEVEL_ERROR: Int = Logger.LEVEL_ERROR
        const val LEVEL_INFO: Int = Logger.LEVEL_INFO
        const val LEVEL_DEBUG: Int = Logger.LEVEL_DEBUG

        private const val TAG = "nachweis-wallet"

        /**
         * Produce the single line to emit for [record], or null to emit nothing.
         *
         * Contract: the returned string never contains [Logger.Record.message] or any
         * representation of [Logger.Record.thrown]; only the level and source location.
         * Returns null for every record when not [debuggable].
         */
        fun format(record: Logger.Record, debuggable: Boolean): String? {
            if (!debuggable) return null
            val where = buildString {
                append(record.sourceClassName ?: "?")
                record.sourceMethod?.let { append('#').append(it) }
            }
            val hadThrown = if (record.thrown != null) " (error suppressed)" else ""
            return "[${levelName(record.level)}] $where$hadThrown"
        }

        private fun levelName(level: Int): String = when (level) {
            LEVEL_ERROR -> "ERROR"
            LEVEL_INFO -> "INFO"
            LEVEL_DEBUG -> "DEBUG"
            else -> "OFF"
        }

        private fun androidSink(level: Int, line: String) {
            when (level) {
                LEVEL_ERROR -> Log.e(TAG, line)
                LEVEL_INFO -> Log.i(TAG, line)
                else -> Log.d(TAG, line)
            }
        }
    }
}
