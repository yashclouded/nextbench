package com.nextbench.app.auth

import com.nextbench.app.navigation.NbRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthNavigationTest {
    @Test
    fun `closing auth preserves its caller when one exists`() {
        assertNull(authExitFallback(poppedCaller = true))
    }

    @Test
    fun `closing root auth falls back to feed`() {
        assertEquals(NbRoute.Feed.path, authExitFallback(poppedCaller = false))
    }
}
