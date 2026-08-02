package com.nextbench.app.chat

import com.google.firebase.Timestamp
import com.nextbench.data.firebase.ChatBlockState
import com.nextbench.data.firebase.ChatRoomDetail
import com.nextbench.data.firebase.ChatRoomListItem
import com.nextbench.data.model.ChatRoom
import com.nextbench.data.model.UserData
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStateTest {

    @Test
    fun `inbox filtering excludes deleted rooms and matches room context`() {
        val viewer = "viewer"
        val active = roomItem(
            id = "active",
            title = "Physics notes",
            message = "Still available",
            other = UserData(uid = "seller", name = "Maya"),
        )
        val deleted = roomItem(id = "deleted", title = "Old", deletedBy = listOf(viewer))
        val archived = roomItem(id = "archived", title = "Desk lamp", archivedBy = listOf(viewer))

        val state = MessagesUiState(
            rooms = listOf(active, deleted, archived),
            viewerId = viewer,
            query = "physics",
        )

        assertEquals(listOf("active"), state.visibleRooms.map { it.room.id })
        assertEquals(1, state.archivedCount)
    }

    @Test
    fun `archived filter only exposes archived rooms`() {
        val viewer = "viewer"
        val state = MessagesUiState(
            rooms = listOf(
                roomItem(id = "active"),
                roomItem(id = "archived", archivedBy = listOf(viewer)),
            ),
            viewerId = viewer,
            showArchived = true,
        )

        assertEquals(listOf("archived"), state.visibleRooms.map { it.room.id })
    }

    @Test
    fun `pending request can only be answered by recipient`() {
        val room = ChatRoomDetail(
            room = ChatRoom(
                id = "room",
                participants = listOf("requester", "recipient"),
                status = "pending",
                requestedBy = "requester",
            ),
            otherUser = UserData(uid = "requester", name = "Maya"),
        )
        val state = ChatRoomUiState(room = room, blockState = ChatBlockState())

        assertTrue(state.canRespondToRequest("recipient"))
        assertFalse(state.canRespondToRequest("requester"))
        assertFalse(state.canRespondToRequest(null))
    }

    @Test
    fun `chat failures become actionable user copy`() {
        assertTrue(IllegalStateException("network unavailable").chatMessage().contains("internet"))
        assertTrue(IllegalStateException("Cannot message this user.").chatMessage().contains("cannot message"))
        assertTrue(IllegalStateException("Firebase is not configured").chatMessage().contains("google-services.json"))
    }

    @Test
    fun `typing timestamp expires after five seconds`() {
        val now = 10_000L

        assertTrue(isUserTyping(timestampMillis = now - 4_999L, nowMillis = now))
        assertFalse(isUserTyping(timestampMillis = now - 5_000L, nowMillis = now))
        assertFalse(isUserTyping(timestampMillis = null, nowMillis = now))
    }

    @Test
    fun `online flag requires a fresh heartbeat`() {
        val now = 200_000L

        assertTrue(isUserOnline(online = true, lastSeenMillis = now - 89_999L, nowMillis = now))
        assertFalse(isUserOnline(online = true, lastSeenMillis = now - 90_000L, nowMillis = now))
        assertFalse(isUserOnline(online = false, lastSeenMillis = now, nowMillis = now))
    }

    @Test
    fun `presence labels match the shared website contract`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = java.time.ZonedDateTime.of(2026, 8, 2, 18, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals("Online", chatPresenceLabel(true, now - 30_000L, null, false, now))
        assertEquals("Active just now", chatPresenceLabel(false, now - 30_000L, null, false, now))
        assertEquals("Active 3m ago", chatPresenceLabel(false, now - 3 * 60_000L, null, false, now))
        assertEquals("Last seen yesterday", lastSeenLabel(now - 24 * 60 * 60_000L, now, zone))
    }

    @Test
    fun `message day helpers respect today yesterday and boundaries`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val today = java.time.ZonedDateTime.of(2026, 8, 2, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val yesterday = today - 24 * 60 * 60_000L
        val previous = messageAt("previous", yesterday)
        val current = messageAt("current", today)

        assertTrue(messageStartsNewDay(previous, current, zone))
        assertFalse(messageStartsNewDay(current, messageAt("same-day", today + 60_000L), zone))
        assertEquals("Today", messageDayLabel(today, today, zone))
        assertEquals("Yesterday", messageDayLabel(yesterday, today, zone))
    }

    @Test
    fun `voice work blocks duplicate composer actions`() {
        val room = ChatRoomDetail(
            room = ChatRoom(id = "room", participants = listOf("viewer", "other")),
            otherUser = UserData(uid = "other"),
        )

        assertFalse(ChatRoomUiState(room = room, isRecordingVoice = true).canSend("viewer"))
        assertFalse(ChatRoomUiState(room = room, isSendingVoice = true).canSend("viewer"))
        assertTrue(ChatRoomUiState(room = room).canSend("viewer"))
    }

    @Test
    fun `voice duration and speed labels are compact`() {
        assertEquals("0:00", formatVoiceTime(0L))
        assertEquals("1:05", formatVoiceTime(65_000L))
        assertEquals("1x", formatPlaybackSpeed(1f))
        assertEquals("1.5x", formatPlaybackSpeed(1.5f))
    }

    private fun roomItem(
        id: String,
        title: String? = null,
        message: String? = null,
        other: UserData? = null,
        archivedBy: List<String> = emptyList(),
        deletedBy: List<String> = emptyList(),
    ) = ChatRoomListItem(
        room = ChatRoom(
            id = id,
            participants = listOf("viewer", other?.uid ?: "other"),
            productTitle = title,
            lastMessage = message,
            archivedBy = archivedBy,
            deletedBy = deletedBy,
        ),
        otherUser = other,
        viewerId = "viewer",
    )

    private fun messageAt(id: String, epochMillis: Long) = com.nextbench.data.model.Message(
        id = id,
        createdAt = Timestamp(Date(epochMillis)),
    )
}
