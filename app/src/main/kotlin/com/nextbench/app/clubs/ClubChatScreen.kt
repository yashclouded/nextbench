package com.nextbench.app.clubs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nextbench.core.common.formatRelativeTime
import com.nextbench.core.designsystem.NbButton
import com.nextbench.core.designsystem.NbButtonVariant
import com.nextbench.core.designsystem.NbDimens
import com.nextbench.core.designsystem.NbEmptyState
import com.nextbench.core.designsystem.NbIcons
import com.nextbench.core.designsystem.NbPill
import com.nextbench.core.designsystem.NbSkeletonLine
import com.nextbench.core.designsystem.NbTextField
import com.nextbench.core.designsystem.NbTheme
import com.nextbench.data.model.Club
import com.nextbench.data.model.Message
import com.nextbench.data.model.MessageType
import com.nextbench.data.model.UserData

@Composable
fun ClubChatScreen(
    user: UserData?,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClubChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val viewerId = user?.uid
    val listState = rememberLazyListState()
    LaunchedEffect(user?.uid) { viewModel.syncViewer(user) }
    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex) }

    Column(modifier = modifier.fillMaxSize().background(NbTheme.colors.surfaceBase)) {
        when {
            state.isLoading && state.club == null -> ClubChatLoading()
            state.error != null && state.club == null -> NbEmptyState(icon = NbIcons.Messages, title = "Club unavailable", message = state.error.orEmpty(), modifier = Modifier.fillMaxSize())
            else -> {
                ClubChatHeader(club = state.club, canLeave = state.isMember(viewerId), leaving = state.isLeaving, onLeave = { if (viewModel.leaveClub()) onLeave() })
                HorizontalDivider(color = NbTheme.colors.border)
                Box(modifier = Modifier.weight(1f)) {
                    if (state.messages.isEmpty()) {
                        NbEmptyState(icon = NbIcons.Messages, title = "Start the conversation", message = "Share something useful, kind, or interesting with the club.", modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = NbDimens.space16, vertical = NbDimens.space20), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
                            item { ClubConversationIntro(club = state.club) }
                            itemsIndexed(state.messages, key = { _, message -> message.id }) { index, message ->
                                ClubMessageBubble(message = message, isViewer = message.senderId == viewerId, showSender = index == 0 || state.messages[index - 1].senderId != message.senderId)
                            }
                        }
                    }
                    state.notice?.let { notice -> ClubNoticeBanner(notice, onDismiss = { viewModel.dismissNotice(notice.id) }, modifier = Modifier.align(Alignment.TopCenter)) }
                }
                val club = state.club
                val canPost = state.canPost(viewerId)
                val canSend = state.canSend(viewerId)
                if (club != null && !state.isMember(viewerId)) {
                    Surface(color = NbTheme.colors.surfaceSoft, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                        Text("Join the club to post messages.", style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted, modifier = Modifier.padding(horizontal = NbDimens.space16, vertical = NbDimens.space14))
                    }
                } else if (club?.settings?.onlyLeadsCanPost == true && !canPost) {
                    Surface(color = NbTheme.colors.surfaceSoft, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                        Text("Only club leads can post in this space.", style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted, modifier = Modifier.padding(horizontal = NbDimens.space16, vertical = NbDimens.space14))
                    }
                } else {
                    ClubComposer(value = state.composerText, onValueChange = viewModel::setComposerText, onSend = { viewModel.sendText() }, enabled = canSend, sending = state.isSending)
                }
            }
        }
    }
}

@Composable
private fun ClubChatHeader(club: Club?, canLeave: Boolean, leaving: Boolean, onLeave: () -> Unit) {
    val current = club ?: return
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = NbDimens.space16, vertical = NbDimens.space12), verticalAlignment = Alignment.CenterVertically) {
        ClubHeaderAvatar(current)
        Spacer(Modifier.width(NbDimens.space12))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
            Text(current.name.ifBlank { "Campus club" }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${current.memberCount} members  ·  ${if (current.type == "private") "Private" else "Public"}", style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted)
        }
        if (canLeave) {
            IconButton(onClick = onLeave, enabled = !leaving, modifier = Modifier.semantics { contentDescription = "Leave club" }) {
                if (leaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NbTheme.colors.brandPink, strokeWidth = 2.dp) else Icon(NbIcons.Logout, contentDescription = null, tint = NbTheme.colors.brandPink)
            }
        }
    }
}

@Composable
private fun ClubHeaderAvatar(club: Club) {
    val image = club.avatar?.takeIf(String::isNotBlank)
    Box(modifier = Modifier.size(NbDimens.avatarLg).clip(RoundedCornerShape(NbDimens.radiusMd)).background(NbTheme.colors.brandTeal.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
        if (image != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(image).crossfade(220).build(), contentDescription = club.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(NbIcons.Messages, contentDescription = null, tint = NbTheme.colors.brandTeal, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ClubConversationIntro(club: Club?) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = NbDimens.space16), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
        ClubHeaderAvatar(club ?: return)
        Text(club.name.ifBlank { "Campus club" }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink)
        Text("A focused space for your campus community.", style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted)
    }
}

@Composable
private fun ClubMessageBubble(message: Message, isViewer: Boolean, showSender: Boolean) {
    val shape = if (isViewer) RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 5.dp)
    val bubbleColor = if (isViewer) NbTheme.colors.brandTeal else NbTheme.colors.surfaceCard
    val textColor = if (isViewer) Color.White else NbTheme.colors.ink
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isViewer) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (isViewer) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
            if (showSender && !isViewer) Text(message.senderName.orEmpty().ifBlank { "Member" }, style = MaterialTheme.typography.labelSmall, color = NbTheme.colors.inkMuted)
            Column(modifier = Modifier.clip(shape).background(bubbleColor).padding(horizontal = NbDimens.space14, vertical = NbDimens.space12), verticalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
                when (MessageType.from(message.type)) {
                    MessageType.Text -> Text(message.text.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = textColor)
                    else -> Text("Media attachment", style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                Text(message.createdAt?.toDate()?.time?.let(::formatRelativeTime) ?: "sending", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.68f))
            }
        }
    }
}

@Composable
private fun ClubComposer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean, sending: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = NbDimens.space12, vertical = NbDimens.space8), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
        NbTextField(value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f), placeholder = "Write to the club...", singleLine = false, maxLines = 5, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default), keyboardActions = KeyboardActions.Default)
        IconButton(onClick = onSend, enabled = enabled, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(NbDimens.radiusMd)).background(if (enabled) NbTheme.colors.brandTeal else NbTheme.colors.surfaceSoft).semantics { contentDescription = "Send club message" }) {
            if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(NbIcons.Send, contentDescription = null, tint = if (enabled) Color.White else NbTheme.colors.inkFaint)
        }
    }
}

@Composable
private fun ClubChatLoading() {
    Column(modifier = Modifier.padding(NbDimens.space16), verticalArrangement = Arrangement.spacedBy(NbDimens.space16)) {
        Row(verticalAlignment = Alignment.CenterVertically) { NbSkeletonLine(Modifier.size(46.dp), height = 46.dp); Spacer(Modifier.width(NbDimens.space12)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) { NbSkeletonLine(widthFraction = 0.5f); NbSkeletonLine(widthFraction = 0.3f, height = 11.dp) } }
        NbSkeletonLine(widthFraction = 0.7f, height = 52.dp)
        NbSkeletonLine(widthFraction = 0.82f, height = 52.dp, modifier = Modifier.align(Alignment.End))
    }
}

@Composable
private fun ClubNoticeBanner(notice: ClubChatNotice, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val color = when (notice.kind) {
        ClubChatNoticeKind.Success -> NbTheme.colors.brandMint
        ClubChatNoticeKind.Error -> NbTheme.colors.brandPink
        ClubChatNoticeKind.Info -> NbTheme.colors.ink
    }
    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut(), modifier = modifier.padding(top = NbDimens.space12, start = NbDimens.space16, end = NbDimens.space16)) {
        LaunchedEffect(notice.id) { kotlinx.coroutines.delay(3200); onDismiss() }
        Surface(color = color, shape = RoundedCornerShape(NbDimens.radiusMd), modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss)) { Text(notice.message, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = NbDimens.space14, vertical = NbDimens.space12)) }
    }
}
