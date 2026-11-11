package com.etaris.astrahomework

import org.junit.Assert.*
import org.junit.Test

class FileFreshnessTest {
    private val discovered = 1_700_000_000_000L

    @Test fun expiresExactlyAt24Hours() {
        assertTrue(isNewFile(discovered, false, discovered))
        assertTrue(isNewFile(discovered, false, discovered + NEW_FILE_WINDOW_MILLIS - 1))
        assertFalse(isNewFile(discovered, false, discovered + NEW_FILE_WINDOW_MILLIS))
        assertFalse(isNewFile(discovered, false, discovered + NEW_FILE_WINDOW_MILLIS + 1))
    }

    @Test fun persistedDiscoveryDoesNotResetOnRefreshOrRestart() {
        // The persisted URI value is reused, irrespective of unknown/old modified metadata.
        val persisted = mapOf("pdf-with-unknown-modified" to discovered, "pdf-with-old-modified" to discovered)
        persisted.values.forEach { first ->
            assertTrue(isNewFile(first, false, discovered + 1000))
            assertFalse(isNewFile(first, false, discovered + 2 * NEW_FILE_WINDOW_MILLIS))
        }
    }

    @Test fun backwardsClockIsClampedWithoutResettingDiscovery() {
        assertTrue(isNewFile(discovered, false, discovered - 1000))
        assertFalse(isNewFile(discovered, false, discovered + NEW_FILE_WINDOW_MILLIS))
    }

    @Test fun uploadedFilesNeverHighlightIncludingLegacyMissingDiscovery() {
        assertFalse(isNewFile(discovered, true, discovered))
        assertFalse(isNewFile(discovered, true, discovered - 1000))
        assertFalse(isNewFile(null, true, discovered))
    }

    @Test fun missingOrInvalidDiscoveryMustBeRecordedBeforeHighlighting() {
        assertFalse(isNewFile(null, false, discovered))
        assertFalse(isNewFile(-1, false, discovered))
        assertTrue(isNewFile(0, false, 0))
        assertFalse(isNewFile(0, false, Long.MAX_VALUE))
    }
}
