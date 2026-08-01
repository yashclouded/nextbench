package com.nextbench.data.firebase

import com.nextbench.data.model.ChatRoom
import com.nextbench.data.model.UserData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryContractTest {

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
