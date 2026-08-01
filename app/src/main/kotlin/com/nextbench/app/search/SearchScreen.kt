package com.nextbench.app.search

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nextbench.core.common.formatRelativeTime
import com.nextbench.core.common.formatRupees
import com.nextbench.core.designsystem.NbAvatar
import com.nextbench.core.designsystem.NbDimens
import com.nextbench.core.designsystem.NbEmptyState
import com.nextbench.core.designsystem.NbIcons
import com.nextbench.core.designsystem.NbPill
import com.nextbench.core.designsystem.NbTextField
import com.nextbench.core.designsystem.NbTheme
import com.nextbench.core.designsystem.pressScale
import com.nextbench.data.model.Post
import com.nextbench.data.model.Product
import com.nextbench.data.model.UserData

@Composable
fun SearchScreen(
    user: UserData?,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenListing: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(user?.uid, user?.school, user?.city) { viewModel.syncViewer(user) }
    Column(modifier = modifier.fillMaxSize().background(NbTheme.colors.surfaceBase)) {
        NbTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = "Search people, posts, or listings",
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.retry() }),
            leadingIcon = { Icon(NbIcons.Search, contentDescription = null, tint = NbTheme.colors.inkMuted, modifier = Modifier.size(20.dp)) },
            trailingIcon = if (state.query.isNotBlank()) ({ IconButton(onClick = viewModel::clearQuery, modifier = Modifier.semantics { contentDescription = "Clear search" }) { Icon(NbIcons.Close, contentDescription = null, tint = NbTheme.colors.inkMuted) } }) else null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = NbDimens.space16, vertical = NbDimens.space12),
        )
        SearchTabs(state, viewModel::selectTab)
        val error = state.error
        val hasResults = state.people.isNotEmpty() || state.posts.isNotEmpty() || state.listings.isNotEmpty()
        when {
            state.isLoading && !state.hasSearched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NbTheme.colors.brandTeal) }
            error != null && !hasResults -> NbEmptyState(NbIcons.Refresh, "Search is unavailable", error, modifier = Modifier.fillMaxSize(), action = { Text("Try again", color = NbTheme.colors.brandTeal, modifier = Modifier.clickable(onClick = viewModel::retry).padding(NbDimens.space8)) })
            else -> SearchResults(state, onOpenProfile, onOpenPost, onOpenListing)
        }
    }
}

@Composable
private fun SearchTabs(state: SearchUiState, onSelect: (SearchTab) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = NbDimens.space16), horizontalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
        SearchTab.entries.forEach { tab ->
            val selected = tab == state.selectedTab
            Surface(color = if (selected) NbTheme.colors.ink else NbTheme.colors.surfaceCard, shape = RoundedCornerShape(NbDimens.radiusFull), modifier = Modifier.clip(RoundedCornerShape(NbDimens.radiusFull)).clickable(role = Role.Tab) { onSelect(tab) }.semantics { role = Role.Tab; contentDescription = tab.label() }) {
                Text(tab.labelWithCount(state), style = MaterialTheme.typography.labelMedium, color = if (selected) NbTheme.colors.surfaceBase else NbTheme.colors.inkMuted, modifier = Modifier.padding(horizontal = NbDimens.space12, vertical = NbDimens.space8))
            }
        }
    }
}

@Composable
private fun SearchResults(state: SearchUiState, onOpenProfile: (String) -> Unit, onOpenPost: (String) -> Unit, onOpenListing: (String) -> Unit) {
    val isEmpty = when (state.selectedTab) { SearchTab.People -> state.people.isEmpty(); SearchTab.Posts -> state.posts.isEmpty(); SearchTab.Listings -> state.listings.isEmpty() }
    if (isEmpty) {
        NbEmptyState(NbIcons.Search, if (state.query.isBlank()) "Start exploring" else "Nothing matched", if (state.query.isBlank()) "Search your campus for people, posts, and useful finds." else "Try a different word or search by a member's @username.", modifier = Modifier.fillMaxSize())
        return
    }
    AnimatedContent(targetState = state.selectedTab, transitionSpec = { (slideInHorizontally { it / 8 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut()) }, label = "search_results") { tab ->
        LazyColumn(contentPadding = PaddingValues(horizontal = NbDimens.space16, vertical = NbDimens.space16), verticalArrangement = Arrangement.spacedBy(NbDimens.space8), modifier = Modifier.fillMaxSize()) {
            when (tab) {
                SearchTab.People -> state.people.forEach { person -> item(key = "person_${person.uid}") { PersonRow(person, onOpenProfile) } }
                SearchTab.Posts -> state.posts.forEach { post -> item(key = "post_${post.id}") { PostRow(post, onOpenPost) } }
                SearchTab.Listings -> state.listings.forEach { listing -> item(key = "listing_${listing.id}") { ListingRow(listing, onOpenListing) } }
            }
        }
    }
}

@Composable
private fun PersonRow(user: UserData, onOpen: (String) -> Unit) {
    Surface(color = NbTheme.colors.surfaceCard, shape = RoundedCornerShape(NbDimens.radiusMd), modifier = Modifier.fillMaxWidth().pressScale(onTap = { onOpen(user.uid) })) {
        Row(Modifier.padding(NbDimens.space12), verticalAlignment = Alignment.CenterVertically) {
            NbAvatar(imageUrl = user.profilePicture, name = user.name, size = 52.dp)
            Column(Modifier.padding(start = NbDimens.space12).weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = user.name.ifBlank { "Student" }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink)
                    if (user.verified) { Spacer(Modifier.size(NbDimens.space4)); NbPill("Verified", contentColor = NbTheme.colors.brandTeal) }
                }
                Text(text = listOfNotNull(user.username?.let { "@$it" }, user.school.takeIf(String::isNotBlank)).joinToString("  · "), style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(NbIcons.ArrowRight, contentDescription = null, tint = NbTheme.colors.inkFaint)
        }
    }
}

@Composable
private fun PostRow(post: Post, onOpen: (String) -> Unit) {
    Surface(color = NbTheme.colors.surfaceCard, shape = RoundedCornerShape(NbDimens.radiusMd), modifier = Modifier.fillMaxWidth().pressScale(onTap = { onOpen(post.id) })) {
        Column(Modifier.padding(NbDimens.space14), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
            Row {
                NbPill(post.type.ifBlank { "Post" }.replaceFirstChar(Char::uppercase), contentColor = NbTheme.colors.brandTeal)
                Spacer(Modifier.weight(1f))
                post.createdAt?.toDate()?.time?.let { Text(text = formatRelativeTime(it), style = MaterialTheme.typography.labelSmall, color = NbTheme.colors.inkFaint) }
            }
            Text(text = post.title.ifBlank { post.content.ifBlank { "Untitled post" } }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(text = listOfNotNull(post.school.takeIf(String::isNotBlank), "${post.repliesCount} replies").joinToString("  ·  "), style = MaterialTheme.typography.labelSmall, color = NbTheme.colors.inkMuted)
        }
    }
}

@Composable
private fun ListingRow(product: Product, onOpen: (String) -> Unit) {
    Surface(color = NbTheme.colors.surfaceCard, shape = RoundedCornerShape(NbDimens.radiusMd), modifier = Modifier.fillMaxWidth().pressScale(onTap = { onOpen(product.id) })) {
        Row(Modifier.padding(NbDimens.space12), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = product.images.firstOrNull() ?: product.image, contentDescription = product.title, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(66.dp).clip(RoundedCornerShape(NbDimens.radiusSm)))
            Column(Modifier.padding(start = NbDimens.space12).weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
                Text(text = product.title.ifBlank { "Untitled listing" }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(text = formatRupees(product.price.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = NbTheme.colors.ink)
                Text(text = product.category, style = MaterialTheme.typography.labelSmall, color = NbTheme.colors.inkMuted)
            }
            Icon(NbIcons.ArrowRight, contentDescription = null, tint = NbTheme.colors.inkFaint)
        }
    }
}

private fun SearchTab.label() = when (this) { SearchTab.People -> "People"; SearchTab.Posts -> "Posts"; SearchTab.Listings -> "Listings" }
private fun SearchTab.labelWithCount(state: SearchUiState) = when (this) { SearchTab.People -> "People ${state.people.size}"; SearchTab.Posts -> "Posts ${state.posts.size}"; SearchTab.Listings -> "Listings ${state.listings.size}" }
