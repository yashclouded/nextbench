package com.nextbench.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostTest {
    @Test
    fun `post defaults cover the complete discovery media schema`() {
        val post = Post()

        assertNull(post.pdfUrl)
        assertNull(post.videoUrl)
        assertNull(post.imageWidth)
        assertTrue(post.imagesDetailed.isEmpty())
        assertEquals(0, post.upvotesCount)
    }
}
