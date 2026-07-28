package com.nextbench.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Top-level scaffold — nav graph + bottom bar wired up in Task 9.
 */
@Composable
fun NbAppShell(onToggleTheme: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize())
}
