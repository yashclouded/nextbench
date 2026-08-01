package com.nextbench.app.search

import org.junit.Assert.assertTrue
import org.junit.Test

class SearchStateTest {
    @Test
    fun `search errors explain setup and network failures`() {
        assertTrue(IllegalStateException("Firebase is not configured").searchMessage().contains("google-services.json"))
        assertTrue(IllegalStateException("network unavailable").searchMessage().contains("internet"))
    }
}
