package com.nextbench.data.firebase

import com.nextbench.data.model.ChatRoom
import com.nextbench.data.model.ClubType
import com.nextbench.data.model.Message
import com.nextbench.data.model.MessageType
import com.nextbench.data.model.UserData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryContractTest {

    @Test
    fun `club documents match the shared web schema`() {
        val club = mapOf<String, Any?>(
            "name" to "Design Circle",
            "avatar" to "https://cdn/club.jpg",
            "school" to "NIT Jaipur",
            "city" to "Jaipur",
            "type" to "private",
            "leadId" to "lead-1",
            "memberIds" to listOf("lead-1", "student-2"),
            "memberCount" to 2L,
            "settings" to mapOf("slowMode" to 30L, "onlyLeadsCanPost" to true),
        ).toClub("club-1")

        assertEquals("club-1", club.id)
        assertEquals("https://cdn/club.jpg", club.avatar)
        assertEquals("lead-1", club.leadId)
        assertEquals(2, club.memberCount)
        assertEquals(30, club.settings.slowMode)
        assertEquals(true, club.settings.onlyLeadsCanPost)
        assertEquals(ClubType.Private.raw, club.type)
    }

    @Test
    fun `legacy club fields remain readable without migration`() {
        val club = mapOf<String, Any?>(
            "imageUrl" to "https://cdn/legacy.jpg",
            "leadIds" to listOf("lead-1"),
            "memberIds" to listOf("lead-1"),
            "settings" to mapOf("slowMode" to true),
        ).toClub("legacy")

        assertEquals("https://cdn/legacy.jpg", club.avatar)
        assertEquals("lead-1", club.leadId)
        assertEquals(1, club.memberCount)
        assertEquals(0, club.settings.slowMode)
    }

    @Test
    fun `text messages match the shared web room contract`() {
        val payload = textMessagePayload(
            sender = UserData(uid = "student-1", name = "Maya", profilePicture = "https://cdn/avatar.jpg"),
            messageId = "message-1",
            text = "Is this still available?",
        )

        assertEquals("student-1", payload["senderId"])
        assertEquals("Maya", payload["senderName"])
        assertEquals("https://cdn/avatar.jpg", payload["senderAvatar"])
        assertEquals("Is this still available?", payload["text"])
        assertEquals("text", payload["type"])
        assertEquals("android_message-1", payload["clientMessageId"])
        assertEquals("sent", payload["status"])
        assertTrue(payload.containsKey("createdAt"))
    }

    @Test
    fun `text payload preserves website reply metadata`() {
        val reply = Message(
            id = "message-older",
            senderId = "student-2",
            senderName = "Noah",
            type = MessageType.Image.raw,
            image = "https://cdn/photo.jpg",
        )
        val payload = textMessagePayload(
            sender = UserData(uid = "student-1", name = "Maya"),
            messageId = "message-new",
            text = "Looks great",
            replyTo = reply,
        )

        assertEquals("message-older", payload["replyToMessageId"])
        assertEquals("Photo", payload["replyToText"])
        assertEquals("image", payload["replyToType"])
        assertEquals("Noah", payload["replyToSenderName"])
    }

    @Test
    fun `room metadata identifies recipients and restores sender visibility`() {
        val payload = roomMetadataPayload(
            senderId = "buyer-1",
            lastMessage = "Can we meet tomorrow?",
            recipientIds = listOf("seller-1"),
        )

        assertEquals("buyer-1", payload["lastSenderId"])
        assertEquals("Can we meet tomorrow?", payload["lastMessage"])
        assertTrue(payload.containsKey("updatedAt"))
        assertTrue(payload.containsKey("unreadBy"))
        assertTrue(payload.containsKey("deletedBy"))
    }

    @Test
    fun `inbox item visibility follows unread delete and archive rules`() {
        val deleted = ChatRoomListItem(
            room = ChatRoom(id = "room", deletedBy = listOf("viewer")),
            otherUser = null,
            viewerId = "viewer",
        )
        val revived = deleted.copy(
            room = deleted.room.copy(unreadBy = listOf("viewer")),
        )
        val archived = deleted.copy(
            room = deleted.room.copy(deletedBy = emptyList(), archivedBy = listOf("viewer")),
        )

        assertTrue(deleted.deleted)
        assertFalse(revived.deleted)
        assertTrue(revived.unread)
        assertTrue(archived.archived)
    }

    @Test
    fun `muted unread activity does not hide a deleted conversation`() {
        val item = ChatRoomListItem(
            room = ChatRoom(
                id = "room",
                deletedBy = listOf("viewer"),
                unreadBy = listOf("viewer"),
                mutedBy = listOf("viewer"),
            ),
            otherUser = null,
            viewerId = "viewer",
        )

        assertTrue(item.hasUnreadActivity)
        assertFalse(item.unread)
        assertFalse(item.deleted)
        assertTrue(item.muted)
    }

    @Test
    fun `block state is symmetric for send policy`() {
        assertTrue(ChatBlockState(blockedByViewer = true).isBlocked)
        assertTrue(ChatBlockState(blockedViewer = true).isBlocked)
        assertFalse(ChatBlockState().isBlocked)
    }

    @Test
    fun `web image objects and legacy reply ids map without migration`() {
        val message = mapOf<String, Any?>(
            "senderId" to "seller-1",
            "image" to mapOf("url" to "https://cdn/photo.jpg", "w" to 800L, "h" to 600L),
            "replyToId" to "older-message",
            "replyToText" to "Still available?",
        ).toChatMessage("message-1")

        assertEquals("image", message?.type)
        assertEquals("https://cdn/photo.jpg", message?.image)
        assertEquals("older-message", message?.replyToMessageId)
        assertEquals("Still available?", message?.replyToText)
    }

    @Test
    fun `file messages keep attachment metadata and numeric coercion`() {
        val message = mapOf<String, Any?>(
            "senderId" to "student-1",
            "type" to "file",
            "file" to mapOf(
                "url" to "https://cdn/notes.pdf",
                "name" to "Notes.pdf",
                "size" to 4096.0,
                "mime" to "application/pdf",
                "pages" to 12L,
            ),
        ).toChatMessage("message-2")

        assertEquals("Notes.pdf", message?.file?.name)
        assertEquals(4096L, message?.file?.size)
        assertEquals(12, message?.file?.pages)
    }
}
