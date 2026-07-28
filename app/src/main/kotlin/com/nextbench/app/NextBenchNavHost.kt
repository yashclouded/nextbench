package com.nextbench.app

import androidx.compose.runtime.Composable

/**
 * Top-level nav host — wired up fully in Task 9 (nav graph + shell).
 * Placeholder keeps MainActivity compilable during P0 scaffolding.
 */
@Composable
fun NextBenchNavHost(onToggleTheme: () -> Unit) {
    NbAppShell(onToggleTheme = onToggleTheme)
}
