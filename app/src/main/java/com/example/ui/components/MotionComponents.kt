package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PaperlyEmerald

fun Modifier.pressScaleEffect(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
    hapticOnPress: Boolean = false
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(isPressed, hapticOnPress) {
        if (hapticOnPress && isPressed) {
            runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun AnimatedOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val haptics = rememberPaperlyHaptics()
    val interactionSource = remember { MutableInteractionSource() }

    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "chip_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "chip_border"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "chip_text"
    )

    Surface(
        onClick = {
            haptics.selectionClick()
            onClick()
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(width = if (selected) 1.5.dp else 1.dp, color = borderColor),
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .pressScaleEffect(interactionSource)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            leadingIcon?.invoke()
            FittedSingleLineText(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

@Composable
fun AnimatedSelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    selectedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    unselectedContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    content: @Composable RowScope.() -> Unit
) {
    val haptics = rememberPaperlyHaptics()
    val interactionSource = remember { MutableInteractionSource() }

    val containerColor by animateColorAsState(
        targetValue = if (selected) selectedContainerColor else unselectedContainerColor,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "card_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "card_border"
    )

    Surface(
        onClick = {
            haptics.selectionClick()
            onClick()
        },
        interactionSource = interactionSource,
        shape = shape,
        color = containerColor,
        border = BorderStroke(width = if (selected) 1.5.dp else 1.dp, color = borderColor),
        modifier = modifier.pressScaleEffect(interactionSource)
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                content = content
            )
        }
    }
}

@Composable
fun AnimatedSuccessBadge(
    modifier: Modifier = Modifier,
    badgeSize: Dp = 56.dp,
    iconSize: Dp = 32.dp
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        appeared = true
    }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.45f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 360f),
        label = "success_badge_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "success_badge_alpha"
    )

    Box(
        modifier = modifier
            .size(badgeSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(PaperlyEmerald.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(badgeSize * 0.78f)
                .clip(CircleShape)
                .background(PaperlyEmerald.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Ready",
                tint = PaperlyEmerald,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
