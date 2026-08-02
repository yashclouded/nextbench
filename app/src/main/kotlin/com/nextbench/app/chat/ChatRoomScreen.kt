package com.nextbench.app.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextbench.core.common.formatRelativeTime
import com.nextbench.core.designsystem.NbAvatar
import com.nextbench.core.designsystem.NbBottomSheet
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
import coil.compose.AsyncImage
import java.util.Locale

@Composable
fun ChatRoomScreen(
    user: UserData?,
    onOpenProduct: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatRoomViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val viewerId = user?.uid
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var initialScrollComplete by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) { viewModel.syncViewer(user) }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val lastMessageIndex = state.messages.size
        if (!initialScrollComplete) {
            listState.scrollToItem(lastMessageIndex)
            initialScrollComplete = true
        } else {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: lastMessageIndex
            if (lastVisibleIndex >= lastMessageIndex - 2) listState.animateScrollToItem(lastMessageIndex)
        }
    }

    val other = state.room?.otherUser
    val otherName = other?.name?.ifBlank { null } ?: "NextBench member"
    val room = state.room?.room
    val visualPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::prepareAttachment)
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::prepareAttachment)
    }

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
                onOpenProfile = other?.uid?.let { { onOpenProfile(it) } },
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
                                onLongPress = viewModel::openMessageActions,
                                onOpenAttachment = { url -> openAttachment(context, url) },
                                onRead = if (message.senderId != viewerId && viewerId != null) {
                                    { viewModel.markMessageRead(message.id) }
                                } else null,
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
                    attachment = state.attachment,
                    preparingAttachment = state.isPreparingAttachment,
                    sendingAttachment = state.isSendingAttachment,
                    replyTo = state.replyTo,
                    onAddAttachment = { showAttachmentPicker = true },
                    onClearAttachment = viewModel::clearAttachment,
                    onClearReply = { viewModel.setReplyTo(null) },
                    onSendAttachment = viewModel::sendAttachment,
                )
            }
        }
    }
    state.actionMessage?.let { message ->
        MessageActionSheet(
            message = message,
            viewerId = viewerId,
            onReply = { viewModel.setReplyTo(message) },
            onReaction = viewModel::toggleReaction,
            onDeleteForMe = viewModel::deleteForMe,
            onDeleteForEveryone = viewModel::deleteForEveryone,
            onDismiss = viewModel::closeMessageActions,
        )
    }
    if (showAttachmentPicker) {
        AttachmentPickerSheet(
            onPickMedia = {
                showAttachmentPicker = false
                visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onPickFile = {
                showAttachmentPicker = false
                documentPicker.launch(arrayOf("*/*"))
            },
            onDismiss = { showAttachmentPicker = false },
        )
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
    onOpenProfile: (() -> Unit)?,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = NbDimens.space16, vertical = NbDimens.space12), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(NbDimens.radiusMd)).clickable(enabled = onOpenProfile != null) { onOpenProfile?.invoke() }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NbAvatar(imageUrl = avatar, name = name, size = NbDimens.avatarLg)
            Spacer(Modifier.width(NbDimens.space12))
            Column(verticalArrangement = Arrangement.spacedBy(NbDimens.space2)) {
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
private fun MessageBubble(
    message: Message,
    isViewer: Boolean,
    showSender: Boolean,
    onLongPress: (Message) -> Unit,
    onOpenAttachment: (String) -> Unit,
    onRead: (() -> Unit)?,
) {
    LaunchedEffect(message.id, message.readBy) { onRead?.invoke() }
    val shape = if (isViewer) RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 5.dp)
    val bubbleColor = if (isViewer) NbTheme.colors.brandTeal else NbTheme.colors.surfaceCard
    val textColor = if (isViewer) Color.White else NbTheme.colors.ink
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isViewer) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (isViewer) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
            if (showSender && !isViewer && !message.senderName.isNullOrBlank()) {
                Text(message.senderName.orEmpty(), style = MaterialTheme.typography.labelSmall, color = NbTheme.colors.inkMuted)
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .background(bubbleColor)
                    .pointerInput(message.id) { detectTapGestures(onLongPress = { onLongPress(message) }) }
                    .animateContentSize()
                    .padding(horizontal = NbDimens.space14, vertical = NbDimens.space12),
                verticalArrangement = Arrangement.spacedBy(NbDimens.space4),
            ) {
                if (message.isDeletedForEveryone) {
                    Text("This message was deleted", style = MaterialTheme.typography.bodyMedium, color = textColor.copy(alpha = 0.75f))
                }
                if (!message.replyToText.isNullOrBlank()) {
                    Text("Replying to ${message.replyToSenderName ?: "message"}", style = MaterialTheme.typography.labelSmall, color = if (isViewer) Color.White.copy(alpha = 0.75f) else NbTheme.colors.brandTeal)
                    Text(message.replyToText.orEmpty(), style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.72f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!message.isDeletedForEveryone) when (MessageType.from(message.type)) {
                    MessageType.Image -> message.image?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "Photo attachment",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(244.dp, 184.dp).clip(RoundedCornerShape(NbDimens.radiusSm)).clickable { onOpenAttachment(url) },
                        )
                    }
                    MessageType.Video -> message.video?.let { video ->
                        Box(
                            modifier = Modifier.size(244.dp, 148.dp).clip(RoundedCornerShape(NbDimens.radiusSm)).background(if (isViewer) Color.White.copy(alpha = 0.14f) else NbTheme.colors.surfaceSoft).clickable { onOpenAttachment(video.url) },
                            contentAlignment = Alignment.Center,
                        ) {
                            video.poster?.takeIf(String::isNotBlank)?.let { poster ->
                                AsyncImage(poster, "Video preview", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(NbDimens.radiusFull)).background(Color.Black.copy(alpha = 0.58f)), contentAlignment = Alignment.Center) {
                                Icon(NbIcons.Play, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    MessageType.File -> message.file?.let { file ->
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(NbDimens.radiusSm)).background(if (isViewer) Color.White.copy(alpha = 0.14f) else NbTheme.colors.surfaceSoft).clickable { onOpenAttachment(file.url) }.padding(NbDimens.space12),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NbDimens.space12),
                        ) {
                            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(NbDimens.radiusSm)).background(if (isViewer) Color.White.copy(alpha = 0.14f) else NbTheme.colors.surfaceBase), contentAlignment = Alignment.Center) {
                                Icon(NbIcons.FileText, contentDescription = null, tint = textColor, modifier = Modifier.size(21.dp))
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space2)) {
                                Text(file.name.ifBlank { "Document" }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(documentMetadata(file.size, file.mime, file.pages), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.68f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(NbIcons.ArrowRight, contentDescription = "Open document", tint = textColor.copy(alpha = 0.72f), modifier = Modifier.size(18.dp))
                        }
                    }
                    MessageType.Voice -> Text("Voice message", style = MaterialTheme.typography.bodyMedium, color = textColor)
                    MessageType.Text -> if (!message.text.isNullOrBlank()) Text(message.text.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = textColor)
                }
                if (message.reactions.isNotEmpty()) {
                    Text(message.reactions.entries.joinToString("  ") { (emoji, users) -> "$emoji ${users.size}" }, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.78f))
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
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Boolean,
    enabled: Boolean,
    sending: Boolean,
    attachment: PreparedChatAttachment?,
    preparingAttachment: Boolean,
    sendingAttachment: Boolean,
    replyTo: Message?,
    onAddAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    onClearReply: () -> Unit,
    onSendAttachment: () -> Boolean,
) {
    Surface(color = NbTheme.colors.surfaceCard, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
        Column(modifier = Modifier.padding(horizontal = NbDimens.space12, vertical = NbDimens.space8), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
            replyTo?.let { reply ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(NbIcons.Reply, contentDescription = null, tint = NbTheme.colors.brandTeal, modifier = Modifier.size(16.dp))
                    Text("Replying to ${reply.senderName ?: "message"}", style = MaterialTheme.typography.labelSmall, color = NbTheme.colors.inkMuted, modifier = Modifier.padding(start = 6.dp).weight(1f))
                    IconButton(onClick = onClearReply, modifier = Modifier.size(28.dp)) { Icon(NbIcons.Close, contentDescription = "Cancel reply", tint = NbTheme.colors.inkMuted, modifier = Modifier.size(16.dp)) }
                }
            }
            attachment?.let { selected ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(NbDimens.radiusSm)).background(NbTheme.colors.surfaceSoft).padding(NbDimens.space8)) {
                    if (selected.mimeType.startsWith("image/")) {
                        AsyncImage(selected.previewUri, "Selected photo", contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(NbDimens.radiusSm)))
                    } else {
                        Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(NbDimens.radiusSm)).background(NbTheme.colors.surfaceBase), contentAlignment = Alignment.Center) {
                            Icon(if (selected.mimeType.startsWith("video/")) NbIcons.Play else NbIcons.FileText, contentDescription = null, tint = NbTheme.colors.brandTeal, modifier = Modifier.size(24.dp))
                        }
                    }
                    Column(modifier = Modifier.padding(horizontal = NbDimens.space8).weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space2)) {
                        Text(selected.displayName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatBytes(selected.file.length())}  ·  ${attachmentLabel(selected.mimeType)}", style = MaterialTheme.typography.labelSmall, color = NbTheme.colors.inkMuted, maxLines = 1)
                    }
                    IconButton(onClick = onClearAttachment, modifier = Modifier.size(28.dp)) { Icon(NbIcons.Close, contentDescription = "Remove attachment", tint = NbTheme.colors.inkMuted, modifier = Modifier.size(16.dp)) }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
                IconButton(onClick = onAddAttachment, enabled = enabled && !preparingAttachment && !sendingAttachment, modifier = Modifier.size(42.dp).semantics { contentDescription = "Add attachment" }) {
                    Icon(NbIcons.Plus, contentDescription = null, tint = NbTheme.colors.brandTeal)
                }
                NbTextField(value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f), placeholder = "Write a message...", singleLine = false, maxLines = 5, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default), keyboardActions = KeyboardActions.Default)
                val canSend = enabled && !preparingAttachment && (attachment != null || value.isNotBlank())
                IconButton(onClick = { if (attachment != null) onSendAttachment() else onSend() }, enabled = canSend, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(NbDimens.radiusFull)).background(if (canSend) NbTheme.colors.brandTeal else NbTheme.colors.surfaceSoft).semantics { contentDescription = "Send message" }) {
                    if (sending || sendingAttachment || preparingAttachment) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(NbIcons.Send, contentDescription = null, tint = if (canSend) Color.White else NbTheme.colors.inkFaint)
                }
            }
        }
    }
}

private enum class MessageDeleteMode { ForMe, ForEveryone }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    message: Message,
    viewerId: String?,
    onReply: () -> Unit,
    onReaction: (String) -> Boolean,
    onDeleteForMe: () -> Boolean,
    onDeleteForEveryone: () -> Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var deleteMode by remember(message.id) { mutableStateOf<MessageDeleteMode?>(null) }
    val attachmentUrl = message.image ?: message.video?.url ?: message.file?.url
    NbBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = NbDimens.space20), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
            if (deleteMode != null) {
                val everyone = deleteMode == MessageDeleteMode.ForEveryone
                Text(if (everyone) "Delete for everyone?" else "Delete for you?", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink)
                Text(if (everyone) "This replaces the message for everyone in the conversation." else "This only removes the message from your view.", style = MaterialTheme.typography.bodyMedium, color = NbTheme.colors.inkMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(NbDimens.space8), modifier = Modifier.padding(top = NbDimens.space8)) {
                    NbButton("Cancel", { deleteMode = null }, modifier = Modifier.weight(1f), variant = NbButtonVariant.Secondary)
                    NbButton(
                        "Delete",
                        {
                            if (everyone) onDeleteForEveryone() else onDeleteForMe()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        variant = NbButtonVariant.Primary,
                    )
                }
            } else {
                Text("Message actions", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink)
                if (!message.text.isNullOrBlank()) {
                    Text(message.text.orEmpty(), style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                } else {
                    message.file?.let { file ->
                        Text(file.name.ifBlank { "Document" }, style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = NbDimens.space4), horizontalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
                    items(listOf("❤️", "😂", "👍", "👏", "🔥")) { emoji ->
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(NbDimens.radiusFull)).background(NbTheme.colors.surfaceSoft).clickable { onReaction(emoji); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(emoji, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                MessageActionRow(NbIcons.Reply, "Reply", onReply)
                if (!message.text.isNullOrBlank()) {
                    MessageActionRow(NbIcons.Copy, "Copy text", onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("NextBench message", message.text))
                        onDismiss()
                    })
                }
                if (!attachmentUrl.isNullOrBlank()) {
                    MessageActionRow(NbIcons.ArrowRight, "Open attachment", onClick = {
                        openAttachment(context, attachmentUrl)
                        onDismiss()
                    })
                }
                Text(if (message.readBy.isEmpty()) "Delivered" else "Seen by ${message.readBy.size}", style = MaterialTheme.typography.labelSmall, color = NbTheme.colors.inkMuted, modifier = Modifier.padding(vertical = NbDimens.space4))
                MessageActionRow(NbIcons.Trash, "Delete for me", { deleteMode = MessageDeleteMode.ForMe }, NbTheme.colors.brandPink)
                if (message.senderId == viewerId) {
                    MessageActionRow(NbIcons.Close, "Delete for everyone", { deleteMode = MessageDeleteMode.ForEveryone }, NbTheme.colors.brandPink)
                }
            }
        }
    }
}

@Composable
private fun MessageActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = NbTheme.colors.ink,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(NbDimens.radiusSm)).clickable(onClick = onClick).padding(vertical = NbDimens.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NbDimens.space12),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentPickerSheet(onPickMedia: () -> Unit, onPickFile: () -> Unit, onDismiss: () -> Unit) {
    NbBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = NbDimens.space20), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
            Text("Add attachment", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink)
            AttachmentOption(NbIcons.Camera, "Photo or video", "Share from your device", onPickMedia, NbTheme.colors.brandTeal)
            AttachmentOption(NbIcons.FileText, "Document", "PDF, notes, slides, and other files", onPickFile, NbTheme.colors.brandPink)
        }
    }
}

@Composable
private fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(NbDimens.radiusSm)).clickable(onClick = onClick).padding(vertical = NbDimens.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NbDimens.space12),
    ) {
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(NbDimens.radiusSm)).background(NbTheme.colors.surfaceSoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space2)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted)
        }
        Icon(NbIcons.ArrowRight, contentDescription = null, tint = NbTheme.colors.inkFaint, modifier = Modifier.size(18.dp))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
}

private fun attachmentLabel(mime: String): String = when {
    mime.startsWith("image/") -> "Photo"
    mime.startsWith("video/") -> "Video"
    mime == "application/pdf" -> "PDF"
    else -> "Document"
}

private fun documentMetadata(size: Long, mime: String, pages: Int?): String = buildList {
    add(formatBytes(size))
    pages?.takeIf { it > 0 }?.let { add("$it ${if (it == 1) "page" else "pages"}") }
    if (mime == "application/pdf") add("PDF")
}.joinToString("  ·  ")

private fun openAttachment(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
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
