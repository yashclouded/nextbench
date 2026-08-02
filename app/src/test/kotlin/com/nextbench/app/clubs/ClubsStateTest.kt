package com.nextbench.app.clubs

import com.nextbench.data.model.Club
import com.nextbench.data.model.ClubSettings
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

    @Test
    fun `lead-only clubs separate posting permission from send readiness`() {
        val state = ClubChatUiState(
            club = Club(
                leadId = "lead",
                memberIds = listOf("lead", "member"),
                settings = ClubSettings(onlyLeadsCanPost = true),
            ),
        )

        assertTrue(state.canPost("lead"))
        assertFalse(state.canPost("member"))
        assertFalse(state.canSend("lead"))
        assertTrue(state.copy(composerText = "Hello").canSend("lead"))
    }
}
