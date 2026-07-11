package com.quellkern.nachweis.ui

import com.quellkern.nachweis.ui.components.StatusKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guards the fixed status vocabulary (design-tokens.md §2) and the D1 labeling law: the
 * outside-registration status must read exactly "Outside this verifier's registration", and the
 * phrase "over-ask" must never appear in any user-facing status label (dev-plan.md D1).
 */
class StatusVocabularyTest {

    @Test
    fun outsideRegistrationLabelIsTheNormativeWording() {
        assertEquals(
            "Outside this verifier's registration",
            StatusKind.OutsideRegistration.label,
        )
    }

    @Test
    fun noStatusLabelSaysOverAsk() {
        StatusKind.entries.forEach { kind ->
            assertFalse(
                "status label must not say 'over-ask': ${kind.name}",
                kind.label.lowercase().contains("over-ask"),
            )
        }
    }

    @Test
    fun statusVocabularyIsStable() {
        assertEquals("Verified", StatusKind.Verified.label)
        assertEquals("Untrusted verifier", StatusKind.Untrusted.label)
        assertEquals("Failed", StatusKind.Failed.label)
        assertEquals("Pending", StatusKind.Pending.label)
    }
}
