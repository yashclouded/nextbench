package com.nextbench.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.nextbench.core.designsystem.NbEmptyState
import com.nextbench.core.designsystem.NbTheme

/**
 * Stand-in for a destination whose real screen lands in a later phase. Keeps the
 * nav graph complete and navigable so the shell can be exercised end to end.
 */
@Composable
fun PlaceholderScreen(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NbTheme.colors.surfaceBase),
        contentAlignment = Alignment.Center,
    ) {
        NbEmptyState(
            icon = icon,
            title = title,
            message = message,
            action = action,
        )
    }
}
