package com.etaris.astrahomework

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test fun newerNumericSegmentsWin() {
        assertTrue(isNewerVersion("1.2", "1.3"))
        assertTrue(isNewerVersion("1.2", "v1.2.1"))
        assertTrue(isNewerVersion("1.9", "1.10"))
        assertTrue(isNewerVersion("v1.0", "1.0.0.1"))
        assertTrue(isNewerVersion("1.0.0", "2"))
    }

    @Test fun equalAndOlderVersionsAreNotOffered() {
        assertFalse(isNewerVersion("1.2", "1.2"))
        assertFalse(isNewerVersion("1.2", "v1.2.0"))
        assertFalse(isNewerVersion("1.10", "1.9"))
        assertFalse(isNewerVersion("2.0", "1.99"))
        assertFalse(isNewerVersion("1.2", "1"))
    }

    @Test fun preReleaseSuffixRanksBelowThePlainRelease() {
        // A beta of the same numbers must not replace a stable install...
        assertFalse(isNewerVersion("1.3", "1.3-beta"))
        // ...but a stable release does supersede an installed pre-release.
        assertTrue(isNewerVersion("1.3-beta", "1.3"))
        assertTrue(isNewerVersion("1.3-beta", "1.3.1"))
    }

    @Test fun malformedInputNeverReportsAnUpdate() {
        assertFalse(isNewerVersion("", ""))
        assertFalse(isNewerVersion("1.2", ""))
        assertFalse(isNewerVersion("", "0"))
    }
}
