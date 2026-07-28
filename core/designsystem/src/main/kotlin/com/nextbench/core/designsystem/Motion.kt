package com.nextbench.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object NbEasing {
    val EaseOutQuart = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
}

object NbDuration {
    const val Entry       = 800
    const val Interaction = 300
    const val Stagger     = 150
    const val ThemeToggle = 350
}

object NbMotion {
    fun <T> entryTween() = tween<T>(NbDuration.Entry, easing = NbEasing.EaseOutQuart)
    fun <T> interactionTween() = tween<T>(NbDuration.Interaction, easing = NbEasing.EaseOutQuart)
    fun <T> pressSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness    = Spring.StiffnessHigh,
    )
}
