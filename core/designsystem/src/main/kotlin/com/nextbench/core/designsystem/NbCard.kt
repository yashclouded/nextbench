package com.nextbench.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

@Composable
fun NbCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = NbTheme.colors
    val shape = RoundedCornerShape(NbDimens.radiusLg)
    val base = modifier
        .shadow(NbDimens.elevationCard, shape, ambientColor = colors.shadowBrand, spotColor = colors.shadowBrand)
        .clip(shape)
        .background(colors.surfaceCard)
        .border(1.dp, colors.border, shape)

    Box(
        modifier = if (onClick != null) base.pressScale(onTap = onClick) else base,
        content = content,
    )
}
