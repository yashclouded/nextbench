package com.nextbench.app.clubs

import com.nextbench.data.model.Club
import com.nextbench.data.model.ClubSettings
import com.nextbench.data.model.Message
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

    @Test
    fun `reply context does not bypass club posting policy`() {
        val reply = Message(id = "message", senderId = "lead", text = "Original")
        val state = ClubChatUiState(
            club = Club(
                leadId = "lead",
                memberIds = listOf("lead", "member"),
                settings = ClubSettings(onlyLeadsCanPost = true),
            ),
            composerText = "Reply",
            replyTo = reply,
        )

        assertTrue(state.canSend("lead"))
        assertFalse(state.canSend("member"))
    }

    @Test
    fun `club settings distinguish lead and member permissions`() {
        val state = ClubSettingsUiState(
            club = Club(leadId = "lead", memberIds = listOf("lead", "member")),
        )

        assertTrue(state.isLead("lead"))
        assertFalse(state.isLead("member"))
        assertTrue(state.isMember("member"))
        assertFalse(state.isMember("outsider"))
    }

    @Test
    fun `club creation requires a useful name and stable visibility`() {
        assertFalse(ClubsUiState(clubName = " ").canCreate)
        assertTrue(ClubsUiState(clubName = "Study group").canCreate)
        assertTrue(ClubsUiState(clubName = "Study group", clubType = "private").canCreate)
        assertFalse(ClubsUiState(clubName = "Study group", clubType = "hidden").canCreate)
        assertFalse(ClubsUiState(clubName = "Study group", isCreating = true).canCreate)
    }
}
