package com.nextbench.app.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nextbench.core.common.formatRelativeTime
import com.nextbench.core.common.formatRupees
import com.nextbench.core.designsystem.NbAvatar
import com.nextbench.core.designsystem.NbButton
import com.nextbench.core.designsystem.NbButtonVariant
import com.nextbench.core.designsystem.NbDimens
import com.nextbench.core.designsystem.NbEmptyState
import com.nextbench.core.designsystem.NbIcons
import com.nextbench.core.designsystem.NbPill
import com.nextbench.core.designsystem.NbSkeletonBox
import com.nextbench.core.designsystem.NbTheme
import com.nextbench.core.designsystem.pressScale
import com.nextbench.data.model.Post
import com.nextbench.data.model.Product
import com.nextbench.data.model.UserData

@Composable
fun PublicProfileScreen(
    profileKey: String,
    username: Boolean,
    onOpenListing: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PublicProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profileKey, username) { viewModel.load(profileKey, username) }
    when {
        state.isLoading -> PublicProfileLoading(modifier)
        state.user == null -> NbEmptyState(
            icon = NbIcons.Profile,
            title = "Profile unavailable",
            message = state.error ?: "This member is not available.",
            action = { NbButton("Try again", { viewModel.retry(profileKey, username) }) },
            modifier = modifier.fillMaxSize(),
        )
        else -> PublicProfileContent(state, viewModel::selectTab, onOpenListing, onOpenPost, modifier)
    }
}

@Composable
private fun PublicProfileContent(
    state: PublicProfileUiState,
    onSelectTab: (ProfileTab) -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    modifier: Modifier,
) {
    val user = state.user ?: return
    LazyColumn(
        modifier = modifier.fillMaxSize().background(NbTheme.colors.surfaceBase),
        contentPadding = PaddingValues(bottom = NbDimens.space32),
        verticalArrangement = Arrangement.spacedBy(NbDimens.space16),
    ) {
        item { PublicIdentity(user) }
        item {
            Row(modifier = Modifier.padding(horizontal = NbDimens.space16), horizontalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
                ProfileTab.entries.forEach { tab ->
                    val selected = state.tab == tab
                    Surface(
                        color = if (selected) NbTheme.colors.ink else NbTheme.colors.surfaceCard,
                        shape = RoundedCornerShape(NbDimens.radiusFull),
                        modifier = Modifier
                            .clip(RoundedCornerShape(NbDimens.radiusFull))
                            .clickable(role = Role.Tab) { onSelectTab(tab) }
                            .semantics { role = Role.Tab; contentDescription = tab.label() },
                    ) {
                        Text(tab.labelWithCount(state), color = if (selected) NbTheme.colors.surfaceBase else NbTheme.colors.inkMuted, modifier = Modifier.padding(horizontal = NbDimens.space14, vertical = NbDimens.space8))
                    }
                }
            }
        }
        item {
            AnimatedContent(
                targetState = state.tab,
                transitionSpec = { (slideInHorizontally { it / 8 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut()) },
                label = "public_profile_activity",
            ) { tab ->
                if (tab == ProfileTab.Listings) PublicListings(state.listings, onOpenListing) else PublicPosts(state.posts, onOpenPost)
            }
        }
    }
}

@Composable
private fun PublicIdentity(user: UserData) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(136.dp).background(NbTheme.colors.surfaceSoft)) {
            user.coverPhoto?.takeIf(String::isNotBlank)?.let { AsyncImage(it, "Profile cover photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Row(modifier = Modifier.padding(horizontal = NbDimens.space20), verticalAlignment = Alignment.Bottom) {
            NbAvatar(imageUrl = user.profilePicture, name = user.name, size = 78.dp, modifier = Modifier.offsetAvatar())
            Column(modifier = Modifier.padding(start = NbDimens.space12, bottom = NbDimens.space12), verticalArrangement = Arrangement.spacedBy(NbDimens.space2)) {
                Text(user.name.ifBlank { "Student" }, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = NbTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                user.username?.takeIf(String::isNotBlank)?.let { Text("@$it", style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted) }
            }
        }
        Column(modifier = Modifier.padding(horizontal = NbDimens.space20), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
            val location = listOfNotNull(user.school.takeIf(String::isNotBlank), user.city.takeIf(String::isNotBlank)).joinToString("  ·  ")
            if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted)
            user.about?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = NbTheme.colors.ink) }
            Row(horizontalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
                if (user.verified) NbPill("Verified", contentColor = NbTheme.colors.brandTeal)
                NbPill("Reputation ${user.reputation.cleanScore()}", contentColor = NbTheme.colors.inkMuted)
            }
        }
    }
}

@Composable
private fun PublicListings(items: List<Product>, onOpen: (String) -> Unit) {
    if (items.isEmpty()) {
        NbEmptyState(NbIcons.Marketplace, "No public listings", "This member has no listings available right now.", modifier = Modifier.padding(horizontal = NbDimens.space16))
        return
    }
    Column(modifier = Modifier.padding(horizontal = NbDimens.space16), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
        items.forEach { product ->
            Surface(color = NbTheme.colors.surfaceCard, shape = RoundedCornerShape(NbDimens.radiusMd), modifier = Modifier.fillMaxWidth().pressScale(onTap = { onOpen(product.id) })) {
                Row(modifier = Modifier.padding(NbDimens.space12), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = product.images.firstOrNull() ?: product.image, contentDescription = product.title, contentScale = ContentScale.Crop, modifier = Modifier.size(66.dp).clip(RoundedCornerShape(NbDimens.radiusSm)))
                    Column(modifier = Modifier.padding(start = NbDimens.space12).weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
                        Text(product.title.ifBlank { "Untitled listing" }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(formatRupees(product.price.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = NbTheme.colors.ink)
                    }
                    Icon(NbIcons.ArrowRight, contentDescription = null, tint = NbTheme.colors.inkFaint)
                }
            }
        }
    }
}

@Composable
private fun PublicPosts(items: List<Post>, onOpen: (String) -> Unit) {
    if (items.isEmpty()) {
        NbEmptyState(NbIcons.Home, "No public posts", "There is nothing public to show here yet.", modifier = Modifier.padding(horizontal = NbDimens.space16))
        return
    }
    Column(modifier = Modifier.padding(horizontal = NbDimens.space16), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
        items.forEach { post ->
            Surface(color = NbTheme.colors.surfaceCard, shape = RoundedCornerShape(NbDimens.radiusMd), modifier = Modifier.fillMaxWidth().pressScale(onTap = { onOpen(post.id) })) {
                    Column(modifier = Modifier.padding(NbDimens.space14), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NbPill(post.type.ifBlank { "Post" }.replaceFirstChar(Char::uppercase), contentColor = NbTheme.colors.brandTeal)
                        Spacer(Modifier.weight(1f))
                        post.createdAt?.toDate()?.time?.let { Text(formatRelativeTime(it), style = MaterialTheme.typography.labelSmall, color = NbTheme.colors.inkFaint) }
                    }
                    Text(post.title.ifBlank { post.content.ifBlank { "Untitled post" } }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${post.upvotesCount} upvotes  ·  ${post.repliesCount} replies", style = MaterialTheme.typography.labelSmall, color = NbTheme.colors.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun PublicProfileLoading(modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize().background(NbTheme.colors.surfaceBase), verticalArrangement = Arrangement.spacedBy(NbDimens.space16)) {
        NbSkeletonBox(Modifier.fillMaxWidth().height(136.dp), radius = 0.dp)
        Row(modifier = Modifier.padding(NbDimens.space20), verticalAlignment = Alignment.CenterVertically) {
            NbSkeletonBox(Modifier.size(78.dp), radius = NbDimens.radiusFull)
            Spacer(Modifier.size(NbDimens.space12))
            NbSkeletonBox(Modifier.fillMaxWidth(0.45f).height(20.dp), radius = NbDimens.radiusSm)
        }
    }
}

private fun ProfileTab.label() = if (this == ProfileTab.Listings) "Listings" else "Posts"
private fun ProfileTab.labelWithCount(state: PublicProfileUiState) = if (this == ProfileTab.Listings) "Listings ${state.listings.size}" else "Posts ${state.posts.size}"
private fun Double.cleanScore(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)
private fun Modifier.offsetAvatar() = padding(top = 0.dp)
