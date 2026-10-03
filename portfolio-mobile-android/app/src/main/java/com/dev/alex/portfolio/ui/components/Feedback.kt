package com.dev.alex.portfolio.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.alex.portfolio.ui.icons.FpIcons
import com.dev.alex.portfolio.ui.theme.Brand
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.Semantic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The mockups' EmptyState: ringed icon, title, one paragraph, optional action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Fp.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted)
                .border(1.dp, colors.border, CircleShape),
        ) {
            FpIcon(icon, size = 32.dp, tint = colors.textSubtle)
        }
        VSpace(16.dp)
        Text(title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        VSpace(6.dp)
        Text(body, color = colors.textMuted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            VSpace(20.dp)
            SecondaryButton(actionLabel, icon = FpIcons.Refresh, onClick = onAction)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    EmptyState(
        icon = FpIcons.CloudOff,
        title = "Couldn't load this page",
        body = "$message Nothing is cached for it yet, so there is nothing to show offline.",
        actionLabel = "Retry",
        onAction = onRetry,
    )
}

private val TIME_FORMAT = SimpleDateFormat("HH:mm, d MMM", Locale.US)

/**
 * Shown above any screen whose figures came from the offline cache, or whose refresh
 * failed while older figures stay on screen.
 */
@Composable
fun StatusBanner(staleSince: Long?, refreshError: String?, onRetry: () -> Unit) {
    if (staleSince == null && refreshError == null) return
    val colors = Fp.colors
    val tone = if (staleSince != null) Semantic.Warning else Semantic.Danger
    val text = if (staleSince != null) {
        "Offline · showing data from ${TIME_FORMAT.format(Date(staleSince))}"
    } else {
        "Couldn't refresh: $refreshError"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(tone.copy(alpha = if (colors.isDark) 0.14f else 0.10f))
            .border(1.dp, tone.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        FpIcon(FpIcons.CloudOff, size = 14.dp, tint = tone)
        Text(text, color = colors.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            "Retry",
            color = Brand.Primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Pulsing placeholder block. */
@Composable
fun Skeleton(width: Dp? = null, height: Dp = 12.dp, modifier: Modifier = Modifier) {
    val colors = Fp.colors
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .alpha(pulse)
            .clip(RoundedCornerShape(4.dp))
            .background(if (colors.isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
    )
}

/** First-load placeholder: a hero card and a few rows. */
@Composable
fun LoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FpCard {
            Skeleton(width = 90.dp, height = 9.dp)
            VSpace(10.dp)
            Skeleton(width = 180.dp, height = 24.dp)
            VSpace(10.dp)
            Skeleton(width = 140.dp, height = 10.dp)
        }
        repeat(4) {
            FpCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Fp.colors.border),
                    )
                    HSpace(10.dp)
                    Column(Modifier.weight(1f)) {
                        Skeleton(width = 70.dp, height = 11.dp)
                        VSpace(6.dp)
                        Skeleton(width = 120.dp, height = 9.dp)
                    }
                    Skeleton(width = 64.dp, height = 12.dp)
                }
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) Brand.Primary else Brand.Primary.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            FpIcon(icon, size = 16.dp, tint = Color.White)
            HSpace(6.dp)
        }
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = Fp.colors
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            FpIcon(icon, size = 15.dp, tint = colors.textMuted)
            HSpace(6.dp)
        }
        Text(text, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
