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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nextbench.app.auth.AuthGate
import com.nextbench.app.auth.AuthViewModel
import com.nextbench.app.auth.requirementForRoute
import com.nextbench.app.auth.AuthScreen
import com.nextbench.app.chat.ChatRoomScreen
import com.nextbench.app.chat.MessagesScreen
import com.nextbench.app.create.CreateScreen
import com.nextbench.app.feed.CommunityScreen
import com.nextbench.app.marketplace.MarketplaceScreen
import com.nextbench.app.marketplace.MarketplacePreviewRoute
import com.nextbench.app.marketplace.MarketplacePreviewScreen
import com.nextbench.app.marketplace.ProductDetailPreviewRoute
import com.nextbench.app.marketplace.ProductDetailPreviewScreen
import com.nextbench.app.marketplace.ProductDetailScreen
import com.nextbench.app.marketplace.ProductComposerScreen
import com.nextbench.app.marketplace.WishlistScreen
import com.nextbench.app.notifications.NotificationsScreen
import com.nextbench.app.invite.InviteScreen
import com.nextbench.app.post.PostDetailScreen
import com.nextbench.app.profile.ProfileScreen
import com.nextbench.app.profile.PublicProfileScreen
import com.nextbench.app.search.SearchScreen
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

        composable(NbRoute.Feed.path) {
            CommunityScreen(
                user = (session as? SessionState.SignedIn)?.userData,
                onOpenPost = { postId ->
                    navController.navigate(NbRoute.post(postId)) { launchSingleTop = true }
                },
                onOpenProfile = { userId ->
                    navController.navigate(NbRoute.profile(userId)) { launchSingleTop = true }
                },
                onSignIn = {
                    navController.navigate(NbRoute.Login.path) { launchSingleTop = true }
                },
                onVerify = {
                    navController.navigate(NbRoute.Verification.path) { launchSingleTop = true }
                },
            )
        }

        composable(NbRoute.Marketplace.path) {
            MarketplaceScreen(
                user = (session as? SessionState.SignedIn)?.userData,
                onOpenProduct = { productId ->
                    navController.navigate(NbRoute.product(productId)) { launchSingleTop = true }
                },
                onOpenProfile = { userId ->
                    navController.navigate(NbRoute.profile(userId)) { launchSingleTop = true }
                },
                onOpenWishlist = {
                    navController.navigate(NbRoute.Wishlist.path) { launchSingleTop = true }
                },
                onSell = {
                    navController.navigate(NbRoute.Sell.path) { launchSingleTop = true }
                },
                onSignIn = {
                    navController.navigate(NbRoute.Login.path) { launchSingleTop = true }
                },
                onVerify = {
                    navController.navigate(NbRoute.Verification.path) { launchSingleTop = true }
                },
            )
        }
        if (com.nextbench.app.BuildConfig.DEBUG) {
            composable(
                route = MarketplacePreviewRoute,
                deepLinks = listOf(navDeepLink { uriPattern = "nextbench://marketplace/preview" }),
            ) {
                MarketplacePreviewScreen()
            }
        }

        composable(NbRoute.Create.path) {
            GuardedDestination(navController, NbRoute.Create.path, authViewModel) {
                CreateScreen(
                    user = (session as? SessionState.SignedIn)?.userData,
                    onOpenSell = {
                        navController.navigate(NbRoute.Sell.path) { launchSingleTop = true }
                    },
                    onOpenPost = { postId ->
                        navController.navigate(NbRoute.post(postId)) { launchSingleTop = true }
                    },
                )
            }
        }

        composable(NbRoute.Messages.path) {
            GuardedDestination(navController, NbRoute.Messages.path, authViewModel) {
                MessagesScreen(
                    user = (session as? SessionState.SignedIn)?.userData,
                    onOpenRoom = { roomId ->
                        navController.navigate(NbRoute.messages(roomId)) { launchSingleTop = true }
                    },
                )
            }
        }

        composable(NbRoute.Profile.path) {
            GuardedDestination(navController, NbRoute.Profile.path, authViewModel) {
                val signOutState by authViewModel.signOutState.collectAsStateWithLifecycle()
                ProfileScreen(
                    user = (session as? SessionState.SignedIn)?.userData,
                    onOpenListing = { productId ->
                        navController.navigate(NbRoute.product(productId)) { launchSingleTop = true }
                    },
                    onOpenPost = { postId ->
                        navController.navigate(NbRoute.post(postId)) { launchSingleTop = true }
                    },
                    onOpenSaved = {
                        navController.navigate(NbRoute.Wishlist.path) { launchSingleTop = true }
                    },
                    onOpenMessages = {
                        navController.navigate(NbRoute.Messages.path) { launchSingleTop = true }
                    },
                    onOpenInvite = {
                        navController.navigate(NbRoute.Invite.path) { launchSingleTop = true }
                    },
                    onOpenVerification = {
                        navController.navigate(NbRoute.Verification.path) { launchSingleTop = true }
                    },
                    onSignOut = authViewModel::signOut,
                    signOutLoading = signOutState.isLoading,
                    signOutError = signOutState.error?.message,
                    onDismissSignOutError = authViewModel::clearSignOutError,
                )
            }
        }

        composable(NbRoute.Search.path) {
            SearchScreen(
                user = (session as? SessionState.SignedIn)?.userData,
                onOpenProfile = { userId -> navController.navigate(NbRoute.profile(userId)) { launchSingleTop = true } },
                onOpenPost = { postId -> navController.navigate(NbRoute.post(postId)) { launchSingleTop = true } },
                onOpenListing = { productId -> navController.navigate(NbRoute.product(productId)) { launchSingleTop = true } },
            )
        }

        composable(
            route = NbRoute.Notifications.path,
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://nextbench.in/notifications" },
                navDeepLink { uriPattern = "nextbench://notifications" },
            ),
        ) {
            GuardedDestination(navController, NbRoute.Notifications.path, authViewModel) {
                NotificationsScreen(
                    user = (session as? SessionState.SignedIn)?.userData,
                    onOpenLink = { link ->
                        val route = notificationRoute(link)
                        if (route != null) navController.navigate(route) { launchSingleTop = true }
                    },
                )
            }
        }

        composable(NbRoute.Wishlist.path) {
            GuardedDestination(navController, NbRoute.Wishlist.path, authViewModel) {
                WishlistScreen(
                    user = (session as? SessionState.SignedIn)?.userData,
                    onOpenProduct = { productId ->
                        navController.navigate(NbRoute.product(productId)) { launchSingleTop = true }
                    },
                    onBrowse = {
                        navController.navigate(NbRoute.Marketplace.path) { launchSingleTop = true }
                    },
                )
            }
        }
        composable(NbRoute.Sell.path) {
            GuardedDestination(navController, NbRoute.Sell.path, authViewModel) {
                ProductComposerScreen(
                    user = (session as? SessionState.SignedIn)?.userData,
                    onOpenProduct = { productId ->
                        navController.navigate(NbRoute.product(productId)) { launchSingleTop = true }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = NbRoute.EditItem.path,
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
        ) {
            GuardedDestination(navController, NbRoute.EditItem.path, authViewModel) {
                ProductComposerScreen(
                    user = (session as? SessionState.SignedIn)?.userData,
                    productId = it.arguments?.getString("productId"),
                    onOpenProduct = { productId ->
                        navController.navigate(NbRoute.product(productId)) {
                            popUpTo(NbRoute.EditItem.path) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = NbRoute.ProductDetail.path,
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://nextbench.in/product/{productId}" },
                navDeepLink { uriPattern = "nextbench://product/{productId}" },
            ),
        ) {
            GuardedDestination(navController, NbRoute.ProductDetail.path, authViewModel) {
                ProductDetailScreen(
                    user = (session as? SessionState.SignedIn)?.userData,
                    onOpenProfile = { userId ->
                        navController.navigate(NbRoute.profile(userId)) { launchSingleTop = true }
                    },
                    onOpenChat = { roomId ->
                        navController.navigate(NbRoute.messages(roomId)) { launchSingleTop = true }
                    },
                    onEdit = { productId ->
                        navController.navigate(NbRoute.editItem(productId)) { launchSingleTop = true }
                    },
                    onSignIn = {
                        navController.navigate(NbRoute.Login.path) { launchSingleTop = true }
                    },
                    onVerify = {
                        navController.navigate(NbRoute.Verification.path) { launchSingleTop = true }
                    },
                )
            }
        }
        if (com.nextbench.app.BuildConfig.DEBUG) {
            composable(
                route = ProductDetailPreviewRoute,
                deepLinks = listOf(navDeepLink { uriPattern = "nextbench://marketplace/preview-product" }),
            ) {
                ProductDetailPreviewScreen()
            }
        }
        composable(
            route = NbRoute.PostDetail.path,
            arguments = listOf(navArgument("postId") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://nextbench.in/post/{postId}" },
                navDeepLink { uriPattern = "nextbench://post/{postId}" },
            ),
        ) {
            PostDetailScreen(
                user = (session as? SessionState.SignedIn)?.userData,
                onOpenProfile = { userId ->
                    navController.navigate(NbRoute.profile(userId)) { launchSingleTop = true }
                },
                onSignIn = {
                    navController.navigate(NbRoute.Login.path) { launchSingleTop = true }
                },
                onVerify = {
                    navController.navigate(NbRoute.Verification.path) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = NbRoute.ProfileDetail.path,
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) {
            GuardedDestination(navController, NbRoute.ProfileDetail.path, authViewModel) {
                PublicProfileScreen(
                    profileKey = it.arguments?.getString("userId").orEmpty(),
                    username = false,
                    onOpenListing = { productId -> navController.navigate(NbRoute.product(productId)) { launchSingleTop = true } },
                    onOpenPost = { postId -> navController.navigate(NbRoute.post(postId)) { launchSingleTop = true } },
                )
            }
        }
        composable(
            route = NbRoute.UsernameProfile.path,
            arguments = listOf(navArgument("username") { type = NavType.StringType }),
        ) {
            GuardedDestination(navController, NbRoute.UsernameProfile.path, authViewModel) {
                PublicProfileScreen(
                    profileKey = it.arguments?.getString("username").orEmpty(),
                    username = true,
                    onOpenListing = { productId -> navController.navigate(NbRoute.product(productId)) { launchSingleTop = true } },
                    onOpenPost = { postId -> navController.navigate(NbRoute.post(postId)) { launchSingleTop = true } },
                )
            }
        }
        composable(
            route = NbRoute.Chat.path,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://nextbench.in/chat/{roomId}" },
                navDeepLink { uriPattern = "nextbench://chat/{roomId}" },
            ),
        ) { backStackEntry ->
            GuardedDestination(navController, NbRoute.Chat.path, authViewModel) {
                ChatRoomScreen(
                    user = (session as? SessionState.SignedIn)?.userData,
                    onOpenProduct = { productId -> navController.navigate(NbRoute.product(productId)) { launchSingleTop = true } },
                )
            }
        }
        composable(
            route = NbRoute.MessagesRoom.path,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://nextbench.in/messages/{roomId}" },
                navDeepLink { uriPattern = "nextbench://messages/{roomId}" },
            ),
        ) {
            GuardedDestination(navController, NbRoute.MessagesRoom.path, authViewModel) {
                ChatRoomScreen(
                    user = (session as? SessionState.SignedIn)?.userData,
                    onOpenProduct = { productId -> navController.navigate(NbRoute.product(productId)) { launchSingleTop = true } },
                )
            }
        }
        composable(NbRoute.MessagesClub.path) { GuardedDestination(navController, NbRoute.MessagesClub.path, authViewModel) { PlaceholderScreen(NbIcons.Messages, "Club conversation", "Your club conversation will appear here.") } }
        composable(NbRoute.Club.path) { GuardedDestination(navController, NbRoute.Club.path, authViewModel) { PlaceholderScreen(NbIcons.Messages, "Club", "A focused space for your campus group is coming together here.") } }
        composable(NbRoute.ClubSettings.path) { GuardedDestination(navController, NbRoute.ClubSettings.path, authViewModel) { PlaceholderScreen(NbIcons.Messages, "Club settings", "Manage members, permissions, and notifications for your club.") } }
        composable(NbRoute.ClubJoin.path) { GuardedDestination(navController, NbRoute.ClubJoin.path, authViewModel) { PlaceholderScreen(NbIcons.Messages, "Join a club", "Enter an invite and find your people on campus.") } }
        composable(NbRoute.Invite.path) {
            GuardedDestination(navController, NbRoute.Invite.path, authViewModel) {
                InviteScreen(user = (session as? SessionState.SignedIn)?.userData)
            }
        }
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

internal fun notificationRoute(link: String): String? {
    val normalized = link.trim().removePrefix("https://nextbench.in/").removePrefix("/")
    return when {
        normalized == "dashboard" || normalized == "community" -> NbRoute.Feed.path
        normalized == "marketplace" -> NbRoute.Marketplace.path
        normalized == "notifications" -> NbRoute.Notifications.path
        normalized.startsWith("product/") -> normalized
        normalized.startsWith("post/") -> normalized
        normalized.startsWith("profile/") -> normalized
        normalized.startsWith("u/") -> normalized
        normalized.startsWith("chat/") -> normalized
        normalized.startsWith("messages/") -> normalized
        else -> null
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
