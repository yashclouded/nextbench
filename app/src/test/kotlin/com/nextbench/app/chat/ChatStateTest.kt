package com.nextbench.app.chat

import com.nextbench.data.firebase.ChatBlockState
import com.nextbench.data.firebase.ChatRoomDetail
import com.nextbench.data.firebase.ChatRoomListItem
import com.nextbench.data.model.ChatRoom
import com.nextbench.data.model.UserData
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
}
