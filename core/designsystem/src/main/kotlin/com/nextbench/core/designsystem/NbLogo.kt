package com.nextbench.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The NextBench brand mark: a squircle with the pink→teal brand gradient and a
 * white bench glyph (backrest, seat, two legs).
 */
@Composable
fun NbLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    pink: Color = NbTheme.colors.brandPink,
    teal: Color = NbTheme.colors.brandTeal,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val corner = w * 0.28f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(pink, teal),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
            cornerRadius = CornerRadius(corner, corner),
        )
        val stroke = w * 0.075f
        // backrest
        drawLine(Color.White, Offset(w * 0.30f, h * 0.35f), Offset(w * 0.70f, h * 0.35f), stroke * 0.7f, StrokeCap.Round)
        // seat
        drawLine(Color.White, Offset(w * 0.28f, h * 0.46f), Offset(w * 0.72f, h * 0.46f), stroke, StrokeCap.Round)
        // legs
        drawLine(Color.White, Offset(w * 0.35f, h * 0.46f), Offset(w * 0.35f, h * 0.68f), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.65f, h * 0.46f), Offset(w * 0.65f, h * 0.68f), stroke, StrokeCap.Round)
    }
}
