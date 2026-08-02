package com.nextbench.app.clubs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClubsStateTest {
    @Test
    fun `club join requires a code`() {
        assertFalse(ClubsUiState().canJoin)
        assertTrue(ClubsUiState(inviteCode = "aB12").canJoin)
    }

    @Test
    fun `club errors remain actionable`() {
        assertTrue(IllegalStateException("network unavailable").clubMessage().contains("internet"))
        assertTrue(IllegalStateException("Firebase is not configured").clubMessage().contains("google-services.json"))
    }
}
