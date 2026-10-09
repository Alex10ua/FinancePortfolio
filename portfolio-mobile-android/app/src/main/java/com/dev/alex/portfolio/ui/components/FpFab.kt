package com.dev.alex.portfolio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dev.alex.portfolio.ui.icons.FpIcons
import com.dev.alex.portfolio.ui.theme.Brand

/**
 * The mockups' `MobFab` (responsive.jsx): a 48dp primary circle with a plus, for a page's
 * primary action. The caller places it, normally bottom-end above the navigation bar, and
 * leaves room under its last card so the button never hides it.
 */
@Composable
fun FpFab(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Brand.Primary)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        FpIcon(FpIcons.Plus, size = 20.dp, tint = Color.White)
    }
}
