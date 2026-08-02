package com.nextbench.data.firebase

import com.nextbench.data.model.UserData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClubRepositoryContractTest {
    @Test
    fun `join payload uses atomic membership fields`() {
        val payload = clubJoinUpdatePayload("student-1")

        assertTrue(payload.containsKey("memberIds"))
        assertTrue(payload.containsKey("memberCount"))
        assertTrue(payload.containsKey("updatedAt"))
    }

    @Test
    fun `leave payload cannot make member count negative`() {
        val payload = clubLeaveUpdatePayload("student-1", 0)

        assertEquals(0, payload["memberCount"])
        assertTrue(payload.containsKey("coLeadIds"))
    }

    @Test
    fun `invite codes normalize whitespace and case`() {
        assertEquals("ab12Cd34", normalizeClubInviteCode(" ab12 Cd34-extra "))
    }

    @Test
    fun `club message metadata includes sender and unread recipients`() {
        val payload = clubMessageMetadataPayload(
            sender = UserData(uid = "student-1", name = "Maya"),
            lastMessage = "See you at the studio",
            recipientIds = listOf("student-2"),
        )

        assertEquals("student-1", payload["lastSenderId"])
        assertEquals("Maya", payload["lastSenderName"])
        assertEquals("See you at the studio", payload["lastMessage"])
        assertTrue(payload.containsKey("unreadBy"))
    }
}
