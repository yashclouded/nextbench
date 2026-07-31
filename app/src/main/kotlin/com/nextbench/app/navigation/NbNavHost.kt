package com.nextbench.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nextbench.app.ui.PlaceholderScreen
import com.nextbench.app.ui.SplashScreen
import com.nextbench.core.designsystem.NbIcons
import com.nextbench.core.designsystem.NbMotion

/**
 * The slide is a twelfth of the screen, not a full page swap — destinations sit
 * under a persistent bottom bar, so a large translation would fight the chrome.
 */
private const val SlideDivisor = 12

private val NbEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(animationSpec = NbMotion.interactionTween()) +
        slideInHorizontally(animationSpec = NbMotion.interactionTween()) { full ->
            full / SlideDivisor
        }
}

private val NbExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(animationSpec = NbMotion.interactionTween()) +
        slideOutHorizontally(animationSpec = NbMotion.interactionTween()) { full ->
            -full / SlideDivisor
        }
}

/**
 * The whole app graph. Feature screens are added behind these stable route
 * contracts, so deep links and notification targets remain valid while each
 * vertical slice is implemented.
 */
@Composable
fun NbNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = NbRoute.Splash.path,
        modifier = modifier,
        enterTransition = NbEnter,
        exitTransition = NbExit,
        popEnterTransition = NbEnter,
        popExitTransition = NbExit,
    ) {
        composable(NbRoute.Splash.path) {
            SplashScreen(
                onFinished = {
                    navController.navigate(NbRoute.Feed.path) {
                        popUpTo(NbRoute.Splash.path) { inclusive = true }
                    }
                },
            )
        }

        composable(NbRoute.Feed.path) { PlaceholderScreen(NbIcons.Home, "Community", "Posts, questions, and conversations from your campus.") }

        composable(NbRoute.Marketplace.path) { PlaceholderScreen(NbIcons.Marketplace, "Marketplace", "Find useful things nearby, from notes to hostel essentials.") }

        composable(NbRoute.Create.path) { PlaceholderScreen(NbIcons.Plus, "Create something useful", "Share a thought with your campus or pass an item on to someone nearby.") }

        composable(NbRoute.Messages.path) { PlaceholderScreen(NbIcons.Messages, "Messages", "Keep marketplace conversations and campus groups in one calm inbox.") }

        composable(NbRoute.Profile.path) { PlaceholderScreen(NbIcons.Profile, "Your space", "Your profile, listings, saved items, and activity will live here.") }

        composable(NbRoute.Search.path) { PlaceholderScreen(NbIcons.Search, "Search", "Find people, posts, clubs, and listings across your campus.") }

        composable(NbRoute.Notifications.path) { PlaceholderScreen(NbIcons.Bell, "Notifications", "Replies, offers, mentions, and important account updates will appear here.") }

        composable(NbRoute.Wishlist.path) { PlaceholderScreen(NbIcons.Bookmark, "Saved", "Your saved listings and posts will be ready here.") }
        composable(NbRoute.Sell.path) { PlaceholderScreen(NbIcons.Marketplace, "List an item", "Add clear photos, a fair price, and meet someone from your campus.") }

        composable(NbRoute.ProductDetail.path) { PlaceholderScreen(NbIcons.Marketplace, "Listing", "Product details, seller context, and safe in-app messages will appear here.") }
        composable(NbRoute.PostDetail.path) { PlaceholderScreen(NbIcons.Home, "Post", "Read the full conversation and join the discussion here.") }
        composable(NbRoute.ProfileDetail.path) { PlaceholderScreen(NbIcons.Profile, "Profile", "See a member's public profile, activity, and listings.") }
        composable(NbRoute.UsernameProfile.path) { PlaceholderScreen(NbIcons.Profile, "Profile", "See a member's public profile, activity, and listings.") }
        composable(NbRoute.Chat.path) { PlaceholderScreen(NbIcons.Messages, "Conversation", "Your direct conversation will appear here.") }
        composable(NbRoute.MessagesRoom.path) { PlaceholderScreen(NbIcons.Messages, "Conversation", "Your direct conversation will appear here.") }
        composable(NbRoute.MessagesClub.path) { PlaceholderScreen(NbIcons.Messages, "Club conversation", "Your club conversation will appear here.") }
        composable(NbRoute.Club.path) { PlaceholderScreen(NbIcons.Messages, "Club", "A focused space for your campus group is coming together here.") }
        composable(NbRoute.ClubSettings.path) { PlaceholderScreen(NbIcons.Messages, "Club settings", "Manage members, permissions, and notifications for your club.") }
        composable(NbRoute.ClubJoin.path) { PlaceholderScreen(NbIcons.Messages, "Join a club", "Enter an invite and find your people on campus.") }
        composable(NbRoute.Invite.path) { PlaceholderScreen(NbIcons.Share, "Invite", "Bring trusted classmates into your campus community.") }
        composable(NbRoute.Admin.path) { PlaceholderScreen(NbIcons.Check, "Admin", "Moderation and verification tools will appear here.") }

        composable(NbRoute.Login.path) { PlaceholderScreen(NbIcons.Profile, "Welcome back", "Sign in with your verified college account.") }
        composable(NbRoute.Signup.path) { PlaceholderScreen(NbIcons.Profile, "Join NextBench", "Create a trusted campus identity in a few steps.") }
        composable(NbRoute.OrgSignup.path) { PlaceholderScreen(NbIcons.Profile, "For organizations", "Create a verified organization profile for your campus.") }
        composable(NbRoute.Verification.path) { PlaceholderScreen(NbIcons.Check, "Verify your identity", "A verified campus keeps conversations and transactions safer for everyone.") }
        composable(NbRoute.Auth.path) { PlaceholderScreen(NbIcons.Profile, "Sign in", "Continue with your verified college account.") }
        composable(NbRoute.Terms.path) { PlaceholderScreen(NbIcons.Check, "Terms", "The terms that guide a respectful campus community.") }
        composable(NbRoute.Privacy.path) { PlaceholderScreen(NbIcons.Check, "Privacy", "How NextBench protects your information.") }
        composable(NbRoute.Careers.path) { PlaceholderScreen(NbIcons.Check, "Careers", "Help build a better student platform.") }
        composable(NbRoute.Transparency.path) { PlaceholderScreen(NbIcons.Check, "Transparency", "How we make decisions about safety and trust.") }
    }
}

/**
 * Tab taps swap the root instead of stacking: Feed is the effective start
 * destination once Splash pops itself inclusively, so popping to the graph's
 * declared start would be a no-op and the back stack would grow on every tap.
 */
fun NavHostController.navigateToTab(tab: NbTab) {
    navigate(tab.route.path) {
        popUpTo(NbRoute.Feed.path) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
