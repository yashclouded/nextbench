package com.nextbench.app.chat

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextbench.core.common.formatRelativeTime
import com.nextbench.core.designsystem.NbAvatar
import com.nextbench.core.designsystem.NbButton
import com.nextbench.core.designsystem.NbButtonVariant
import com.nextbench.core.designsystem.NbDimens
import com.nextbench.core.designsystem.NbEmptyState
import com.nextbench.core.designsystem.NbIcons
import com.nextbench.core.designsystem.NbPill
import com.nextbench.core.designsystem.NbSkeletonLine
import com.nextbench.core.designsystem.NbTextField
import com.nextbench.core.designsystem.NbTheme
import com.nextbench.core.designsystem.NbVerifiedBadge
import com.nextbench.data.model.Message
import com.nextbench.data.model.MessageType
import com.nextbench.data.model.UserData

@Composable
fun ChatRoomScreen(
    user: UserData?,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatRoomViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val viewerId = user?.uid
    val listState = rememberLazyListState()

    LaunchedEffect(user?.uid) { viewModel.syncViewer(user) }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    val other = state.room?.otherUser
    val otherName = other?.name?.ifBlank { null } ?: "NextBench member"
    val room = state.room?.room

    Column(modifier = modifier.fillMaxSize().background(NbTheme.colors.surfaceBase)) {
        if (state.isLoading && state.room == null) {
            ChatLoadingHeader()
        } else if (state.error != null && state.room == null) {
            NbEmptyState(
                icon = NbIcons.Messages,
                title = "Conversation unavailable",
                message = state.error.orEmpty(),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ChatHeader(
                name = otherName,
                avatar = other?.profilePicture,
                verified = other?.verified == true,
                school = other?.school,
                productTitle = room?.productTitle,
                onOpenProduct = room?.productId?.let { { onOpenProduct(it) } },
            )
            HorizontalDivider(color = NbTheme.colors.border)

            Box(modifier = Modifier.weight(1f)) {
                if (state.messages.isEmpty()) {
                    EmptyConversation(name = otherName)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = NbDimens.space16, vertical = NbDimens.space20),
                        verticalArrangement = Arrangement.spacedBy(NbDimens.space8),
                    ) {
                        item { ConversationIntro(name = otherName, avatar = other?.profilePicture, verified = other?.verified == true) }
                        itemsIndexed(state.messages, key = { _, message -> message.id }) { index, message ->
                            MessageBubble(
                                message = message,
                                isViewer = message.senderId == viewerId,
                                showSender = index == 0 || state.messages[index - 1].senderId != message.senderId,
                            )
                        }
                    }
                }
                state.notice?.let { notice ->
                    NoticeBanner(notice = notice, onDismiss = { viewModel.dismissNotice(notice.id) }, modifier = Modifier.align(Alignment.TopCenter))
                }
            }

            if (state.blocked) {
                BlockedComposer(otherName)
            } else if (state.pendingRequest) {
                PendingRequestBar(
                    name = otherName,
                    isRecipient = state.canRespondToRequest(viewerId),
                    busy = state.isActingOnRequest,
                    onAccept = viewModel::acceptRequest,
                    onDecline = viewModel::declineRequest,
                )
            } else {
                Composer(
                    value = state.composerText,
                    onValueChange = viewModel::setComposerText,
                    onSend = viewModel::sendText,
                    enabled = state.canSend(viewerId),
                    sending = state.isSending,
                )
            }
        }
    }
}

@Composable
private fun ChatLoadingHeader() {
    Column(modifier = Modifier.padding(NbDimens.space16), verticalArrangement = Arrangement.spacedBy(NbDimens.space12)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NbSkeletonLine(Modifier.size(46.dp), height = 46.dp)
            Spacer(Modifier.width(NbDimens.space12))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
                NbSkeletonLine(widthFraction = 0.42f)
                NbSkeletonLine(widthFraction = 0.28f, height = 11.dp)
            }
        }
        Spacer(Modifier.size(24.dp))
        NbSkeletonLine(widthFraction = 0.62f, height = 48.dp)
        NbSkeletonLine(widthFraction = 0.78f, height = 48.dp, modifier = Modifier.align(Alignment.End))
    }
}

@Composable
private fun ChatHeader(
    name: String,
    avatar: String?,
    verified: Boolean,
    school: String?,
    productTitle: String?,
    onOpenProduct: (() -> Unit)?,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = NbDimens.space16, vertical = NbDimens.space12), verticalAlignment = Alignment.CenterVertically) {
        NbAvatar(imageUrl = avatar, name = name, size = NbDimens.avatarLg)
        Spacer(Modifier.width(NbDimens.space12))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
                Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (verified) NbVerifiedBadge(size = 15.dp)
            }
            Text(
                text = listOfNotNull(school?.takeIf(String::isNotBlank), "Verified member".takeIf { verified }).joinToString("  · ").ifBlank { "Campus conversation" },
                style = MaterialTheme.typography.bodySmall,
                color = NbTheme.colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onOpenProduct != null && !productTitle.isNullOrBlank()) {
            IconButton(onClick = onOpenProduct, modifier = Modifier.semantics { contentDescription = "Open listing" }) {
                Icon(NbIcons.Marketplace, contentDescription = null, tint = NbTheme.colors.brandTeal)
            }
        }
    }
}

@Composable
private fun ConversationIntro(name: String, avatar: String?, verified: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = NbDimens.space16), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
        NbAvatar(imageUrl = avatar, name = name, size = NbDimens.avatarXl)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
            Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink)
            if (verified) NbVerifiedBadge(size = 15.dp)
        }
        Text("Keep it kind, clear, and campus-safe.", style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted)
    }
}

@Composable
private fun MessageBubble(message: Message, isViewer: Boolean, showSender: Boolean) {
    val shape = if (isViewer) RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 5.dp)
    val bubbleColor = if (isViewer) NbTheme.colors.brandTeal else NbTheme.colors.surfaceCard
    val textColor = if (isViewer) Color.White else NbTheme.colors.ink
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isViewer) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (isViewer) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
            if (showSender && !isViewer && !message.senderName.isNullOrBlank()) {
                Text(message.senderName.orEmpty(), style = MaterialTheme.typography.labelSmall, color = NbTheme.colors.inkMuted)
            }
            Column(modifier = Modifier.clip(shape).background(bubbleColor).padding(horizontal = NbDimens.space14, vertical = NbDimens.space12), verticalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
                if (!message.replyToText.isNullOrBlank()) {
                    Text("Replying to ${message.replyToSenderName ?: "message"}", style = MaterialTheme.typography.labelSmall, color = if (isViewer) Color.White.copy(alpha = 0.75f) else NbTheme.colors.brandTeal)
                    Text(message.replyToText.orEmpty(), style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.72f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                when (MessageType.from(message.type)) {
                    MessageType.Image, MessageType.Video -> Text("Media attachment", style = MaterialTheme.typography.bodyMedium, color = textColor)
                    MessageType.File -> Text("${message.file?.name ?: "File attachment"}", style = MaterialTheme.typography.bodyMedium, color = textColor)
                    MessageType.Voice -> Text("Voice message", style = MaterialTheme.typography.bodyMedium, color = textColor)
                    MessageType.Text -> if (!message.text.isNullOrBlank()) Text(message.text.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = textColor)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
                    Text(message.createdAt?.toDate()?.time?.let(::formatRelativeTime) ?: "sending", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.68f))
                    if (isViewer && message.readBy.isNotEmpty()) Text("Seen", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.68f))
                }
            }
        }
    }
}

@Composable
private fun EmptyConversation(name: String) {
    NbEmptyState(icon = NbIcons.Messages, title = "Say hello to $name", message = "A thoughtful first message goes a long way.", modifier = Modifier.fillMaxSize())
}

@Composable
private fun PendingRequestBar(name: String, isRecipient: Boolean, busy: Boolean, onAccept: () -> Boolean, onDecline: () -> Boolean) {
    Surface(color = NbTheme.colors.surfaceCard, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Column(modifier = Modifier.padding(horizontal = NbDimens.space16, vertical = NbDimens.space12), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
            Text(if (isRecipient) "$name wants to message you" else "Waiting for $name to accept your request", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink)
            Text(if (isRecipient) "Accept to start the conversation, or decline to remove it." else "You can reply once the request is accepted.", style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted)
            if (isRecipient) Row(horizontalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
                NbButton("Decline", { onDecline() }, modifier = Modifier.weight(1f), enabled = !busy, variant = NbButtonVariant.Ghost)
                NbButton("Accept", { onAccept() }, modifier = Modifier.weight(1f), enabled = !busy, loading = busy, variant = NbButtonVariant.Secondary)
            }
        }
    }
}

@Composable
private fun BlockedComposer(name: String) {
    Surface(color = NbTheme.colors.surfaceSoft, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(modifier = Modifier.padding(horizontal = NbDimens.space16, vertical = NbDimens.space14), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
            Icon(NbIcons.Shield, contentDescription = null, tint = NbTheme.colors.inkMuted, modifier = Modifier.size(20.dp))
            Text("Messaging is unavailable with $name.", style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted)
        }
    }
}

@Composable
private fun Composer(value: String, onValueChange: (String) -> Unit, onSend: () -> Boolean, enabled: Boolean, sending: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = NbDimens.space12, vertical = NbDimens.space8), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
        NbTextField(value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f), placeholder = "Write a message...", singleLine = false, maxLines = 5, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default), keyboardActions = KeyboardActions.Default)
        IconButton(onClick = { onSend() }, enabled = enabled, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(NbDimens.radiusMd)).background(if (enabled) NbTheme.colors.brandTeal else NbTheme.colors.surfaceSoft).semantics { contentDescription = "Send message" }) {
            if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(NbIcons.Send, contentDescription = null, tint = if (enabled) Color.White else NbTheme.colors.inkFaint)
        }
    }
}

@Composable
private fun NoticeBanner(notice: ChatNotice, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val color = when (notice.kind) {
        ChatNoticeKind.Success -> NbTheme.colors.brandMint
        ChatNoticeKind.Error -> NbTheme.colors.brandPink
        ChatNoticeKind.Info -> NbTheme.colors.ink
    }
    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut(), modifier = modifier.padding(top = NbDimens.space12, start = NbDimens.space16, end = NbDimens.space16)) {
        LaunchedEffect(notice.id) {
            kotlinx.coroutines.delay(3200)
            onDismiss()
        }
        Surface(color = color, shape = RoundedCornerShape(NbDimens.radiusMd), modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss)) {
            Text(notice.message, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = NbDimens.space14, vertical = NbDimens.space12))
        }
    }
}
