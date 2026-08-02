package com.nextbench.app.chat

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.nextbench.core.common.formatRelativeTime
import com.nextbench.core.designsystem.NbAvatar
import com.nextbench.core.designsystem.NbBottomSheet
import com.nextbench.core.designsystem.NbButton
import com.nextbench.core.designsystem.NbButtonVariant
import com.nextbench.core.designsystem.NbCountBadge
import com.nextbench.core.designsystem.NbDimens
import com.nextbench.core.designsystem.NbEmptyState
import com.nextbench.core.designsystem.NbIcons
import com.nextbench.core.designsystem.NbPill
import com.nextbench.core.designsystem.NbSkeletonBox
import com.nextbench.core.designsystem.NbSkeletonLine
import com.nextbench.core.designsystem.NbTextField
import com.nextbench.core.designsystem.NbTheme
import com.nextbench.core.designsystem.NbVerifiedBadge
import com.nextbench.data.firebase.ChatRoomListItem
import com.nextbench.data.model.UserData

@Composable
fun MessagesScreen(
    user: UserData?,
    onOpenRoom: (String) -> Unit,
    onOpenClubs: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var actionItem by remember { mutableStateOf<ChatRoomListItem?>(null) }
    LaunchedEffect(user?.uid) { viewModel.syncViewer(user) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NbTheme.colors.surfaceBase),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = NbDimens.space16, vertical = NbDimens.space12),
            verticalArrangement = Arrangement.spacedBy(NbDimens.space12),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.showArchived) "Archived" else "Messages",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = NbTheme.colors.ink,
                    )
                }
                ArchiveToggle(
                    showArchived = state.showArchived,
                    count = state.archivedCount,
                    onClick = viewModel::toggleArchived,
                )
                IconButton(onClick = onOpenClubs, modifier = Modifier.semantics { contentDescription = "Open clubs" }) {
                    Icon(NbIcons.Messages, contentDescription = null, tint = NbTheme.colors.brandTeal)
                }
            }
            NbTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = "Search people or listings",
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                leadingIcon = { Icon(NbIcons.Search, contentDescription = null, tint = NbTheme.colors.inkMuted, modifier = Modifier.size(19.dp)) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(NbIcons.Close, contentDescription = "Clear search", tint = NbTheme.colors.inkMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                } else null,
            )
        }

        HorizontalDivider(color = NbTheme.colors.border)

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> InboxSkeleton(modifier = Modifier.fillMaxSize())
                state.error != null -> InboxError(message = state.error.orEmpty(), onRetry = viewModel::retry)
                state.visibleRooms.isEmpty() -> InboxEmpty(showArchived = state.showArchived, query = state.query)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = NbDimens.space24),
                ) {
                    items(state.visibleRooms, key = { it.room.id }) { item ->
                        SwipeConversationRow(
                            item = item,
                            busy = item.room.id in state.busyRoomIds,
                            onClick = { onOpenRoom(item.room.id) },
                            onLongPress = { actionItem = item },
                            onToggleRead = { viewModel.toggleRead(item) },
                            onToggleArchive = { viewModel.toggleArchive(item) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 88.dp),
                            color = NbTheme.colors.border,
                        )
                    }
                }
            }
            state.notice?.let { notice ->
                InboxNoticeBanner(
                    notice = notice,
                    onDismiss = { viewModel.dismissNotice(notice.id) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
    actionItem?.let { item ->
        ConversationActionSheet(
            item = item,
            busy = item.room.id in state.busyRoomIds,
            onOpen = { actionItem = null; onOpenRoom(item.room.id) },
            onPin = { if (viewModel.togglePin(item)) actionItem = null },
            onRead = { if (viewModel.toggleRead(item)) actionItem = null },
            onMute = { if (viewModel.toggleMute(item)) actionItem = null },
            onArchive = { if (viewModel.toggleArchive(item)) actionItem = null },
            onDelete = { if (viewModel.delete(item)) actionItem = null },
            onDismiss = { actionItem = null },
        )
    }
}

@Composable
private fun ArchiveToggle(showArchived: Boolean, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(NbDimens.radiusFull))
            .background(if (showArchived) NbTheme.colors.brandTeal.copy(alpha = 0.12f) else NbTheme.colors.surfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = NbDimens.space12, vertical = NbDimens.space8)
            .semantics { role = Role.Button; contentDescription = if (showArchived) "Show active conversations" else "Show archived conversations" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NbDimens.space4),
    ) {
        Icon(NbIcons.Archive, contentDescription = null, tint = if (showArchived) NbTheme.colors.brandTeal else NbTheme.colors.inkMuted, modifier = Modifier.size(17.dp))
        Text(
            text = if (showArchived) "Inbox" else "Archived",
            style = MaterialTheme.typography.labelMedium,
            color = if (showArchived) NbTheme.colors.brandTeal else NbTheme.colors.inkMuted,
        )
        if (!showArchived) NbCountBadge(count = count, color = NbTheme.colors.inkMuted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeConversationRow(
    item: ChatRoomListItem,
    busy: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleArchive: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (!busy) when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onToggleRead()
                SwipeToDismissBoxValue.EndToStart -> onToggleArchive()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
        positionalThreshold = { distance -> distance * 0.24f },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = !busy,
        enableDismissFromEndToStart = !busy,
        backgroundContent = {
            Row(
                modifier = Modifier.fillMaxSize().background(NbTheme.colors.surfaceSoft).padding(horizontal = NbDimens.space20),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SwipeHint(
                    icon = if (item.hasUnreadActivity) NbIcons.Check else NbIcons.Messages,
                    label = if (item.hasUnreadActivity) "Read" else "Unread",
                    color = NbTheme.colors.brandTeal,
                )
                Spacer(Modifier.weight(1f))
                SwipeHint(
                    icon = NbIcons.Archive,
                    label = if (item.archived) "Restore" else "Archive",
                    color = NbTheme.colors.brandPink,
                )
            }
        },
    ) {
        ConversationRow(item = item, busy = busy, onClick = onClick, onLongPress = onLongPress)
    }
}

@Composable
private fun SwipeHint(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NbDimens.space2)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ConversationRow(item: ChatRoomListItem, busy: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    val other = item.otherUser
    val name = other?.name?.ifBlank { null } ?: "NextBench member"
    val preview = item.room.lastMessage?.takeIf(String::isNotBlank)
        ?: if (item.room.status == "pending") "Chat request waiting for your response" else "Start a conversation"
    val timestamp = item.room.updatedAt?.toDate()?.time?.let(::formatRelativeTime)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NbTheme.colors.surfaceBase)
            .pointerInput(item.room.id, busy) {
                detectTapGestures(
                    onTap = { if (!busy) onClick() },
                    onLongPress = { if (!busy) onLongPress() },
                )
            }
            .padding(horizontal = NbDimens.space16, vertical = NbDimens.space14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            NbAvatar(imageUrl = other?.profilePicture, name = name, size = NbDimens.avatarLg)
            if (item.unread) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(NbTheme.colors.brandPink)
                        .align(Alignment.BottomEnd),
                )
            }
        }
        Spacer(Modifier.width(NbDimens.space12))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
                Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (item.unread) FontWeight.Bold else FontWeight.SemiBold), color = NbTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (other?.verified == true) NbVerifiedBadge(size = 14.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
                Text(preview, style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (item.unread) FontWeight.Medium else FontWeight.Normal), color = if (item.unread) NbTheme.colors.ink else NbTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (!item.room.productTitle.isNullOrBlank()) NbPill(label = item.room.productTitle.orEmpty(), contentColor = NbTheme.colors.brandTeal, modifier = Modifier)
            }
        }
        Spacer(Modifier.width(NbDimens.space8))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbDimens.space4)) {
                if (item.pinned) Icon(NbIcons.BookmarkFilled, contentDescription = "Pinned", tint = NbTheme.colors.brandTeal, modifier = Modifier.size(13.dp))
                if (item.muted) Icon(NbIcons.VolumeOff, contentDescription = "Muted", tint = NbTheme.colors.inkMuted, modifier = Modifier.size(13.dp))
                timestamp?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (item.unread) NbTheme.colors.brandTeal else NbTheme.colors.inkMuted) }
            }
            if (item.unread) NbCountBadge(count = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationActionSheet(
    item: ChatRoomListItem,
    busy: Boolean,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onRead: () -> Unit,
    onMute: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val other = item.otherUser
    val name = other?.name?.ifBlank { null } ?: "NextBench member"
    var confirmDelete by remember(item.room.id) { mutableStateOf(false) }
    NbBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = NbDimens.space20), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = NbDimens.space8)) {
                NbAvatar(imageUrl = other?.profilePicture, name = name, size = NbDimens.avatarLg)
                Column(modifier = Modifier.padding(start = NbDimens.space12).weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.room.lastMessage?.takeIf(String::isNotBlank) ?: "No messages yet", style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (confirmDelete) {
                Text("Remove this conversation?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = NbTheme.colors.ink)
                Text("It will return if a new message arrives.", style = MaterialTheme.typography.bodySmall, color = NbTheme.colors.inkMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(NbDimens.space8), modifier = Modifier.padding(top = NbDimens.space8)) {
                    NbButton("Cancel", { confirmDelete = false }, modifier = Modifier.weight(1f), variant = NbButtonVariant.Secondary)
                    NbButton("Remove", onDelete, modifier = Modifier.weight(1f), enabled = !busy, variant = NbButtonVariant.Primary)
                }
            } else {
                ConversationActionRow(NbIcons.ArrowRight, "Open conversation", onOpen, busy)
                ConversationActionRow(if (item.pinned) NbIcons.Bookmark else NbIcons.BookmarkFilled, if (item.pinned) "Unpin" else "Pin", onPin, busy)
                ConversationActionRow(if (item.hasUnreadActivity) NbIcons.Check else NbIcons.Messages, if (item.hasUnreadActivity) "Mark as read" else "Mark as unread", onRead, busy)
                ConversationActionRow(if (item.muted) NbIcons.Volume else NbIcons.VolumeOff, if (item.muted) "Unmute notifications" else "Mute notifications", onMute, busy)
                ConversationActionRow(NbIcons.Archive, if (item.archived) "Restore to inbox" else "Archive", onArchive, busy)
                ConversationActionRow(NbIcons.Trash, "Remove for me", { confirmDelete = true }, busy, NbTheme.colors.brandPink)
            }
        }
    }
}

@Composable
private fun ConversationActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    disabled: Boolean,
    tint: Color = NbTheme.colors.ink,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(NbDimens.radiusSm)).clickable(enabled = !disabled, onClick = onClick).padding(vertical = NbDimens.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NbDimens.space12),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = tint, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InboxNoticeBanner(notice: ChatNotice, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(notice.id) {
        kotlinx.coroutines.delay(2800)
        onDismiss()
    }
    val color = if (notice.kind == ChatNoticeKind.Error) NbTheme.colors.brandPink else NbTheme.colors.ink
    Text(
        text = notice.message,
        color = Color.White,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.fillMaxWidth().background(color).clickable(onClick = onDismiss).padding(horizontal = NbDimens.space16, vertical = NbDimens.space12),
    )
}

@Composable
private fun InboxSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(NbDimens.space16), verticalArrangement = Arrangement.spacedBy(NbDimens.space20)) {
        items(6) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NbSkeletonBox(Modifier.size(NbDimens.avatarLg), NbDimens.avatarLg / 2)
                Spacer(Modifier.width(NbDimens.space12))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.space8)) {
                    NbSkeletonLine(widthFraction = 0.48f, height = 14.dp)
                    NbSkeletonLine(widthFraction = 0.78f, height = 12.dp)
                }
            }
        }
    }
}

@Composable
private fun InboxError(message: String, onRetry: () -> Unit) {
    NbEmptyState(
        icon = NbIcons.Refresh,
        title = "Messages are taking a moment",
        message = message,
        action = { Text("Tap to retry", color = NbTheme.colors.brandTeal, modifier = Modifier.clickable(onClick = onRetry).padding(NbDimens.space8)) },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun InboxEmpty(showArchived: Boolean, query: String) {
    val filtered = query.isNotBlank()
    NbEmptyState(
        icon = if (filtered) NbIcons.Search else if (showArchived) NbIcons.Archive else NbIcons.Messages,
        title = when {
            filtered -> "No conversations found"
            showArchived -> "Nothing archived yet"
            else -> "Your inbox is clear"
        },
        message = when {
            filtered -> "Try a different name or listing title."
            showArchived -> "Archived conversations will stay here until you bring them back."
            else -> "When you connect with a seller or classmate, the conversation will appear here."
        },
        modifier = Modifier.fillMaxSize(),
    )
}
