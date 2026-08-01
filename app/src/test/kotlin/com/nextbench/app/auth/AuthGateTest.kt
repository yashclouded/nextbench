package com.nextbench.app.auth

import com.nextbench.app.navigation.NbRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthGateTest {

    @Test
    fun routeRequirementsMatchWebsiteAccessPolicy() {
        assertEquals(RouteRequirement.Public, requirementForRoute(NbRoute.Feed.path))
        assertEquals(RouteRequirement.Public, requirementForRoute(NbRoute.Marketplace.path))
        assertEquals(RouteRequirement.SignedIn, requirementForRoute(NbRoute.ProductDetail.path))
        assertEquals(RouteRequirement.SignedIn, requirementForRoute(NbRoute.Profile.path))
        assertEquals(RouteRequirement.Verified, requirementForRoute(NbRoute.Create.path))
        assertEquals(RouteRequirement.Verified, requirementForRoute(NbRoute.EditItem.path))
        assertEquals(RouteRequirement.Verified, requirementForRoute(NbRoute.MessagesRoom.path))
        assertEquals(RouteRequirement.Admin, requirementForRoute(NbRoute.Admin.path))
    }

    @Test
    fun signedOutUsersAreRedirectedOnlyFromProtectedRoutes() {
        assertEquals(
            GateDecision.Allow,
            gateDecision(RouteRequirement.Public, GateSession.SignedOut),
        )
        assertEquals(
            GateDecision.Redirect(NbRoute.Login.path),
            gateDecision(RouteRequirement.SignedIn, GateSession.SignedOut),
        )
    }

    @Test
    fun verifiedAndAdminRequirementsUseProfileClaims() {
        val member = GateSession.SignedIn(verified = false, isAdmin = false)
        val verified = GateSession.SignedIn(verified = true, isAdmin = false)
        val admin = GateSession.SignedIn(verified = true, isAdmin = true)

        assertEquals(
            GateDecision.Redirect(NbRoute.Verification.path),
            gateDecision(RouteRequirement.Verified, member),
        )
        assertEquals(GateDecision.Allow, gateDecision(RouteRequirement.Verified, verified))
        assertEquals(
            GateDecision.Redirect(NbRoute.Feed.path),
            gateDecision(RouteRequirement.Admin, verified),
        )
        assertEquals(GateDecision.Allow, gateDecision(RouteRequirement.Admin, admin))
    }
}
