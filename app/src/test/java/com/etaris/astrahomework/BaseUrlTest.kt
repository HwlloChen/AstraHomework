package com.etaris.astrahomework

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BaseUrlTest {
    @Test fun trimsAndPreservesHttpsBasePath() {
        assertEquals("https://school.example:59178/prefix", validatedBaseUrl(" https://school.example:59178/prefix/// "))
        assertEquals("https://school.example", validatedBaseUrl("https://school.example/"))
    }

    @Test fun allowsOnlyExplicitLocalPlainHttp() {
        listOf("http://localhost:8080", "http://127.0.0.1:8080", "http://[::1]:8080").forEach {
            assertEquals(it, validatedBaseUrl(it))
        }
        assertThrows(IllegalArgumentException::class.java) { validatedBaseUrl("http://school.example") }
    }

    @Test fun rejectsCredentialsQueriesFragmentsAndInvalidAddresses() {
        listOf("https://user:secret@school.example", "https://school.example?token=secret",
            "https://school.example#fragment", "https://school.example:65536", "not a URL", "ftp://school.example"
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { validatedBaseUrl(value) }
        }
    }
}
