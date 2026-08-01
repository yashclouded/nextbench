package com.nextbench.app.feed

import com.nextbench.data.firebase.PostVote
import com.nextbench.data.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedViewModelTest {
    @Test
    fun `signed out viewers are never considered authenticated`() {
        assertFalse(FeedViewer().signedIn)
        assertTrue(FeedViewer(uid = "student-1").signedIn)
    }

    @Test
    fun `upvote from neutral increments once`() {
        val preview = previewVote(Post(id = "p", upvotesCount = 4), false, false, PostVote.Up)

        assertEquals(PostVote.Up, preview.vote)
        assertEquals(5, preview.post.upvotesCount)
        assertTrue(preview.upvoted)
        assertFalse(preview.downvoted)
    }

    @Test
    fun `upvote replaces a downvote without allowing negative counts`() {
        val preview = previewVote(
            Post(id = "p", upvotesCount = 2, downvotesCount = 0),
            wasUpvoted = false,
            wasDownvoted = true,
            requestedVote = PostVote.Up,
        )

        assertEquals(3, preview.post.upvotesCount)
        assertEquals(0, preview.post.downvotesCount)
        assertTrue(preview.upvoted)
        assertFalse(preview.downvoted)
    }

    @Test
    fun `tapping the active vote removes it`() {
        val preview = previewVote(Post(id = "p", downvotesCount = 3), false, true, PostVote.Down)

        assertNull(preview.vote)
        assertEquals(2, preview.post.downvotesCount)
        assertFalse(preview.upvoted)
        assertFalse(preview.downvoted)
    }

    @Test
    fun `pagination merge preserves order and removes duplicate ids`() {
        val merged = mergePosts(
            current = listOf(Post(id = "one"), Post(id = "two", title = "old")),
            incoming = listOf(Post(id = "two", title = "new"), Post(id = "three")),
        )

        assertEquals(listOf("one", "two", "three"), merged.map(Post::id))
        assertEquals("old", merged[1].title)
    }

    @Test
    fun `downvote replaces an upvote and updates both counters`() {
        val preview = previewVote(
            Post(id = "p", upvotesCount = 7, downvotesCount = 2),
            wasUpvoted = true,
            wasDownvoted = false,
            requestedVote = PostVote.Down,
        )

        assertEquals(6, preview.post.upvotesCount)
        assertEquals(3, preview.post.downvotesCount)
        assertFalse(preview.upvoted)
        assertTrue(preview.downvoted)
    }
}
