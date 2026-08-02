package com.nextbench.app.clubs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nextbench.core.designsystem.NbButton
import com.nextbench.core.designsystem.NbButtonVariant
import com.nextbench.core.designsystem.NbCard
import com.nextbench.core.designsystem.NbDimens
import com.nextbench.core.designsystem.NbEmptyState
import com.nextbench.core.designsystem.NbIcons
import com.nextbench.core.designsystem.NbMotion
import com.nextbench.core.designsystem.NbPill
import com.nextbench.core.designsystem.NbSkeletonLine
import com.nextbench.core.designsystem.NbTextField
import com.nextbench.core.designsystem.NbTheme
import com.nextbench.core.designsystem.NbToast
import com.nextbench.core.designsystem.NbToastKind
import com.nextbench.core.designsystem.NbToastState
import com.nextbench.data.model.Club
import com.nextbench.data.model.ClubType
import com.nextbench.data.model.UserData

@Composable
fun ClubsScreen(
    user: UserData?,
    onOpenClub: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClubsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(user?.uid) { viewModel.syncViewer(user) }

    Box(modifier = modifier.fillMaxSize().background(NbTheme.colors.surfaceBase)) {
        when {
            state.isLoading -> ClubsLoading()
            state.error != null && state.memberClubs.isEmpty() && state.publicClubs.isEmpty() -> NbEmptyState(
                icon = NbIcons.Refresh,
                title = "Clubs are taking a moment",
                message = state.error.orEmpty(),
                action = { Text("Tap to retry", color = NbTheme.colors.brandTeal, modifier = Modifier.clickable(onClick = viewModel::retry).padding(NbDimens.space8)) },
                modifier = Modifier.fillMaxSize(),
            )
            else -> ClubsContent(
                state = state,
                onOpenClub = onOpenClub,
                onInviteCodeChange = viewModel::setInviteCode,
                onJoinCode = { viewModel.joinByCode() },
                onJoinPublic = viewModel::joinPublicClub,
                modifier = Modifier.fillMaxSize(),
            )
        }
        state.notice?.let { notice ->
            NbToast(
                state = NbToastState(
                    message = notice.message,
                    kind = when (notice.kind) {
                        ClubNoticeKind.Success -> NbToastKind.Success
                        ClubNoticeKind.Error -> NbToastKind.Error
                        ClubNoticeKind.Info -> NbToastKind.Info
                    },
                    visible = true,
                ),
                onDismiss = { viewModel.dismissNotice(notice.id) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ClubsContent(
    state: ClubsUiState,
    onOpenClub: (String) -> Unit,
    onInviteCodeChange: (String) -> Unit,
    onJoinCode: () -> Unit,
    onJoinPublic: (Club) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = NbDimens.space16, vertical = NbDimens.space20),
        verticalArrangement = Arrangement.spacedBy(NbDimens.space16),
    ) {
        item {
            AnimatedVisibility(visible = true, enter = fadeIn(NbMotion.entryTween()) + slideInVertically(NbMotion.entryTween()) { it / 3 }) {
                Column(verticalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
                    NbPill(label = "Campus groups", contentColor = NbTheme.colors.brandPink)
                    Text("Find your people", style = MaterialTheme.typography.headlineSmall, color = NbTheme.colors.ink)
                    Text("Small, focused spaces for the clubs, interests, and communities that make campus feel like yours.", style = MaterialTheme.typography.bodyMedium, color = NbTheme.colors.inkMuted)
                }
            }
        }
        item {
            JoinClubCard(value = state.inviteCode, onValueChange = onInviteCodeChange, onJoin = onJoinCode, enabled = state.canJoin)
        }
        if (state.memberClubs.isNotEmpty()) {
            item { SectionTitle(title = "Your clubs", subtitle = "Jump back into the conversations you follow.") }
            items(state.memberClubs, key = { it.id }) { club -> ClubRow(club, onClick = { onOpenClub(club.id) }) }
        }
        item { SectionTitle(title = "Discover clubs", subtitle = "Public groups picked for your campus.") }
        if (state.publicClubs.isEmpty()) {
            item { Text("No public clubs are available right now. Try an invite code from a classmate.", style = MaterialTheme.typography.bodyMedium, color = NbTheme.colors.inkMuted, modifier = Modifier.padding(vertical = NbDimens.space8)) }
        } else {
            items(state.publicClubs, key = { it.id }) { club -> DiscoverClubCard(club, onJoin = { onJoinPublic(club) }) }
        }
        item { Spacer(Modifier.height(NbDimens.space24)) }
    }
}

@Composable
private fun JoinClubCard(value: String, onValueChange: (String) -> Unit, onJoin: () -> Unit, enabled: Boolean) {
    NbCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NbDimens.space16), verticalArrangement = Arrangement.spacedBy(NbDimens.space12)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(NbTheme.colors.brandTeal.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(NbIcons.ArrowRight, contentDescription = null, tint = NbTheme.colors.brandTeal, modifier = Modifier.size(21.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(NbDimens.space2)) {
                    Text("Join with an invite", style = MaterialTheme.typography.titleMedium, color = NbTheme.colors.ink)
                    Text("Use the 8-character code shared by a club lead.", style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NbDimens.space8), verticalAlignment = Alignment.Bottom) {
                NbTextField(value = value, onValueChange = onValueChange, placeholder = "Invite code", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
                IconButton(onClick = onJoin, enabled = enabled, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(NbDimens.radiusMd)).background(if (enabled) NbTheme.colors.brandTeal else NbTheme.colors.surfaceSoft).semantics { contentDescription = "Join club with invite code" }) {
                    Icon(NbIcons.ArrowRight, contentDescription = null, tint = if (enabled) androidx.compose.ui.graphics.Color.White else NbTheme.colors.inkFaint)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(NbDimens.space2)) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted)
    }
}

@Composable
private fun ClubRow(club: Club, onClick: () -> Unit) {
    NbCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(NbDimens.space12), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbDimens.space12)) {
            ClubAvatar(club, size = NbDimens.avatarLg)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
                Text(club.name.ifBlank { "Campus club" }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink)
                Text("${club.memberCount} members${club.lastMessage?.let { "  ·  $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted, maxLines = 1)
            }
            Icon(NbIcons.ArrowRight, contentDescription = null, tint = NbTheme.colors.inkMuted, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun DiscoverClubCard(club: Club, onJoin: () -> Unit) {
    NbCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(NbDimens.space12), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbDimens.space12)) {
            ClubAvatar(club, size = 52.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
                Text(club.name.ifBlank { "Campus club" }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink, maxLines = 1)
                Text(listOfNotNull(club.school.takeIf(String::isNotBlank), "${club.memberCount} members").joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted, maxLines = 1)
            }
            NbButton(text = "Join", onClick = onJoin, variant = NbButtonVariant.Secondary, modifier = Modifier)
        }
    }
}

@Composable
private fun ClubAvatar(club: Club, size: androidx.compose.ui.unit.Dp) {
    val image = club.avatar?.takeIf(String::isNotBlank)
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(NbDimens.radiusMd)).background(NbTheme.colors.brandTeal.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
        if (image != null) {
            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(image).crossfade(220).build(), contentDescription = club.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(if (ClubType.from(club.type) == ClubType.Private) NbIcons.Shield else NbIcons.Messages, contentDescription = null, tint = NbTheme.colors.brandTeal, modifier = Modifier.size(size / 2.2f))
        }
    }
}

@Composable
private fun ClubsLoading() {
    Column(modifier = Modifier.fillMaxWidth().padding(NbDimens.space20), verticalArrangement = Arrangement.spacedBy(NbDimens.space16)) {
        NbSkeletonLine(widthFraction = 0.42f, height = 14.dp)
        NbSkeletonLine(widthFraction = 0.74f, height = 28.dp)
        NbSkeletonLine(widthFraction = 1f, height = 118.dp)
        repeat(4) { NbSkeletonLine(widthFraction = 1f, height = 72.dp) }
    }
}
