package com.nextbench.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStateTest {
    @Test
    fun `profile errors remain actionable`() {
        assertTrue(IllegalStateException("Firebase is not configured").profileMessage().contains("google-services.json"))
        assertTrue(IllegalStateException("network unavailable").profileMessage().contains("internet"))
        assertTrue(IllegalStateException("Your session expired").profileMessage().contains("session expired"))
    }

    @Test
    fun `profile tab defaults to listings and switches explicitly`() {
        val state = ProfileUiState()

        assertEquals(ProfileTab.Listings, state.tab)
        assertEquals(ProfileTab.Posts, ProfileTab.entries.first { it != state.tab })
    }
}
