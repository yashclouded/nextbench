package com.nextbench.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nextbench.app.auth.AuthGate
import com.nextbench.app.auth.AuthViewModel
import com.nextbench.app.auth.requirementForRoute
import com.nextbench.app.auth.AuthScreen
import com.nextbench.data.firebase.SessionState
import com.nextbench.app.ui.PlaceholderScreen
import com.nextbench.app.ui.SplashScreen
import com.nextbench.app.verification.VerificationScreen
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
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    val session by authViewModel.session.collectAsStateWithLifecycle()

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
                sessionReady = session !is SessionState.Loading,
                onFinished = {
                    navController.navigate(NbRoute.Feed.path) {
                        popUpTo(NbRoute.Splash.path) { inclusive = true }
                    }
                },
            )
        }

        composable(NbRoute.Feed.path) { PlaceholderScreen(NbIcons.Home, "Community", "Posts, questions, and conversations from your campus.") }

        composable(NbRoute.Marketplace.path) { PlaceholderScreen(NbIcons.Marketplace, "Marketplace", "Find useful things nearby, from notes to hostel essentials.") }

        composable(NbRoute.Create.path) {
            GuardedDestination(navController, NbRoute.Create.path, authViewModel) {
                PlaceholderScreen(NbIcons.Plus, "Create something useful", "Share a thought with your campus or pass an item on to someone nearby.")
            }
        }

        composable(NbRoute.Messages.path) {
            GuardedDestination(navController, NbRoute.Messages.path, authViewModel) {
                PlaceholderScreen(NbIcons.Messages, "Messages", "Keep marketplace conversations and campus groups in one calm inbox.")
            }
        }

        composable(NbRoute.Profile.path) {
            GuardedDestination(navController, NbRoute.Profile.path, authViewModel) {
                PlaceholderScreen(NbIcons.Profile, "Your space", "Your profile, listings, saved items, and activity will live here.")
            }
        }

        composable(NbRoute.Search.path) { PlaceholderScreen(NbIcons.Search, "Search", "Find people, posts, clubs, and listings across your campus.") }

        composable(NbRoute.Notifications.path) {
            GuardedDestination(navController, NbRoute.Notifications.path, authViewModel) {
                PlaceholderScreen(NbIcons.Bell, "Notifications", "Replies, offers, mentions, and important account updates will appear here.")
            }
        }

        composable(NbRoute.Wishlist.path) {
            GuardedDestination(navController, NbRoute.Wishlist.path, authViewModel) {
                PlaceholderScreen(NbIcons.Bookmark, "Saved", "Your saved listings and posts will be ready here.")
            }
        }
        composable(NbRoute.Sell.path) {
            GuardedDestination(navController, NbRoute.Sell.path, authViewModel) {
                PlaceholderScreen(NbIcons.Marketplace, "List an item", "Add clear photos, a fair price, and meet someone from your campus.")
            }
        }
        composable(NbRoute.EditItem.path) {
            GuardedDestination(navController, NbRoute.EditItem.path, authViewModel) {
                PlaceholderScreen(NbIcons.Marketplace, "Edit listing", "Update your item details and keep your campus listing current.")
            }
        }

        composable(NbRoute.ProductDetail.path) {
            GuardedDestination(navController, NbRoute.ProductDetail.path, authViewModel) {
                PlaceholderScreen(NbIcons.Marketplace, "Listing", "Product details, seller context, and safe in-app messages will appear here.")
            }
        }
        composable(NbRoute.PostDetail.path) { PlaceholderScreen(NbIcons.Home, "Post", "Read the full conversation and join the discussion here.") }
        composable(NbRoute.ProfileDetail.path) { GuardedDestination(navController, NbRoute.ProfileDetail.path, authViewModel) { PlaceholderScreen(NbIcons.Profile, "Profile", "See a member's public profile, activity, and listings.") } }
        composable(NbRoute.UsernameProfile.path) { GuardedDestination(navController, NbRoute.UsernameProfile.path, authViewModel) { PlaceholderScreen(NbIcons.Profile, "Profile", "See a member's public profile, activity, and listings.") } }
        composable(NbRoute.Chat.path) { GuardedDestination(navController, NbRoute.Chat.path, authViewModel) { PlaceholderScreen(NbIcons.Messages, "Conversation", "Your direct conversation will appear here.") } }
        composable(NbRoute.MessagesRoom.path) { GuardedDestination(navController, NbRoute.MessagesRoom.path, authViewModel) { PlaceholderScreen(NbIcons.Messages, "Conversation", "Your direct conversation will appear here.") } }
        composable(NbRoute.MessagesClub.path) { GuardedDestination(navController, NbRoute.MessagesClub.path, authViewModel) { PlaceholderScreen(NbIcons.Messages, "Club conversation", "Your club conversation will appear here.") } }
        composable(NbRoute.Club.path) { GuardedDestination(navController, NbRoute.Club.path, authViewModel) { PlaceholderScreen(NbIcons.Messages, "Club", "A focused space for your campus group is coming together here.") } }
        composable(NbRoute.ClubSettings.path) { GuardedDestination(navController, NbRoute.ClubSettings.path, authViewModel) { PlaceholderScreen(NbIcons.Messages, "Club settings", "Manage members, permissions, and notifications for your club.") } }
        composable(NbRoute.ClubJoin.path) { GuardedDestination(navController, NbRoute.ClubJoin.path, authViewModel) { PlaceholderScreen(NbIcons.Messages, "Join a club", "Enter an invite and find your people on campus.") } }
        composable(NbRoute.Invite.path) { GuardedDestination(navController, NbRoute.Invite.path, authViewModel) { PlaceholderScreen(NbIcons.Share, "Invite", "Bring trusted classmates into your campus community.") } }
        composable(NbRoute.Admin.path) { GuardedDestination(navController, NbRoute.Admin.path, authViewModel) { PlaceholderScreen(NbIcons.Check, "Admin", "Moderation and verification tools will appear here.") } }

        composable(NbRoute.Login.path) { AuthScreen(authViewModel = authViewModel, initialMode = com.nextbench.app.auth.OtpMode.Login, navController = navController) }
        composable(NbRoute.Signup.path) { AuthScreen(authViewModel = authViewModel, initialMode = com.nextbench.app.auth.OtpMode.Signup, navController = navController) }
        composable(NbRoute.OrgSignup.path) { PlaceholderScreen(NbIcons.Profile, "For organizations", "Create a verified organization profile for your campus.") }
        composable(NbRoute.Verification.path) {
            GuardedDestination(navController, NbRoute.Verification.path, authViewModel) {
                val userData = (session as? SessionState.SignedIn)?.userData
                if (userData != null) {
                    VerificationScreen(
                        user = userData,
                        onClose = { navigateBackOrFeed(navController) },
                        onContinue = {
                            navController.navigate(NbRoute.Feed.path) {
                                popUpTo(NbRoute.Verification.path) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        }
        composable(NbRoute.Auth.path) { AuthScreen(authViewModel = authViewModel, initialMode = com.nextbench.app.auth.OtpMode.Login, navController = navController) }
        composable(NbRoute.Terms.path) { PlaceholderScreen(NbIcons.Check, "Terms", "The terms that guide a respectful campus community.") }
        composable(NbRoute.Privacy.path) { PlaceholderScreen(NbIcons.Check, "Privacy", "How NextBench protects your information.") }
        composable(NbRoute.Careers.path) { PlaceholderScreen(NbIcons.Check, "Careers", "Help build a better student platform.") }
        composable(NbRoute.Transparency.path) { PlaceholderScreen(NbIcons.Check, "Transparency", "How we make decisions about safety and trust.") }
    }
}

@Composable
private fun GuardedDestination(
    navController: NavHostController,
    path: String,
    authViewModel: AuthViewModel,
    content: @Composable () -> Unit,
) {
    AuthGate(
        navController = navController,
        requirement = requirementForRoute(path),
        viewModel = authViewModel,
        content = content,
    )
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

private fun navigateBackOrFeed(navController: NavHostController) {
    if (!navController.popBackStack()) {
        navController.navigate(NbRoute.Feed.path) { launchSingleTop = true }
    }
}
