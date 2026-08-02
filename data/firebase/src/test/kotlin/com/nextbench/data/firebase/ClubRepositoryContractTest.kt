package com.nextbench.data.firebase

import com.nextbench.data.model.UserData
import com.nextbench.data.model.Message
import com.nextbench.data.model.MessageType
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

    @Test
    fun `club text messages preserve structured reply metadata`() {
        val payload = textMessagePayload(
            sender = UserData(uid = "student-1", name = "Maya"),
            messageId = "message-2",
            text = "I can share it",
            replyTo = Message(
                id = "message-1",
                senderId = "student-2",
                senderName = "Noah",
                type = MessageType.File.raw,
                file = com.nextbench.data.model.FileAttachment(name = "Notes.pdf"),
            ),
        )

        assertEquals("message-1", payload["replyToMessageId"])
        assertEquals("Noah", payload["replyToSenderName"])
        assertEquals("Notes.pdf", payload["replyToText"])
        assertEquals(MessageType.File.raw, payload["replyToType"])
    }

    @Test
    fun `club delete for everyone redacts all supported media`() {
        val payload = clubDeletedForEveryonePayload()

        assertEquals(true, payload["isDeletedForEveryone"])
        assertEquals("This message was deleted", payload["text"])
        assertTrue(payload.containsKey("image"))
        assertTrue(payload.containsKey("video"))
        assertTrue(payload.containsKey("file"))
        assertTrue(payload.containsKey("audioUrl"))
    }

    @Test
    fun `club creation payload matches website schema and creator ownership`() {
        val payload = clubCreationPayload(
            creator = UserData(uid = "student-1", school = "Loreto", city = "Lucknow"),
            name = "  Physics Study Group  ",
            description = "  Weekly problem solving  ",
            type = "private",
            inviteCode = "AbC234xy",
        )

        assertEquals("Physics Study Group", payload["name"])
        assertEquals("Weekly problem solving", payload["description"])
        assertEquals("private", payload["type"])
        assertEquals("student-1", payload["leadId"])
        assertEquals(listOf("student-1"), payload["memberIds"])
        assertEquals(1, payload["memberCount"])
        assertEquals("Loreto", payload["school"])
        assertEquals("Lucknow", payload["city"])
        assertTrue(payload.containsKey("createdAt"))
        assertTrue(payload.containsKey("updatedAt"))
    }

    @Test
    fun `generated club invite codes are unambiguous and eight characters`() {
        val code = generateClubInviteCode(kotlin.random.Random(7))

        assertEquals(8, code.length)
        assertTrue(code.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789" })
    }
}
