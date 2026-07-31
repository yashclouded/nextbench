package com.nextbench.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbRouteTest {

    @Test
    fun websiteRouteFamiliesHaveStableAndroidPaths() {
        assertEquals("community", NbRoute.Feed.path)
        assertEquals("product/{productId}", NbRoute.ProductDetail.path)
        assertEquals("messages/club/{clubId}", NbRoute.MessagesClub.path)
        assertEquals("club/{clubId}/settings", NbRoute.ClubSettings.path)
        assertEquals("u/{username}", NbRoute.UsernameProfile.path)
    }

    @Test
    fun routeBuildersEncodeIndividualPathSegments() {
        assertEquals("product/book+%26+notes", NbRoute.product("book & notes"))
        assertEquals("u/maryam%2Fdesigns", NbRoute.username("maryam/designs"))
    }

    @Test
    fun onlyTabsAreTopLevel() {
        assertTrue(NbRoute.isTopLevel(NbRoute.Marketplace.path))
        assertFalse(NbRoute.isTopLevel(NbRoute.ProductDetail.path))
        assertEquals(NbTab.Profile, NbRoute.tabFor(NbRoute.Profile.path))
    }
}
