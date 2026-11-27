package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun rememberPaperlyShimmerBrush(): Brush {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val baseColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val highlightColor = if (isDark) Color(0xFF334155) else Color(0xFFF8FAFC)

    val transition = rememberInfiniteTransition(label = "paperly_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1300f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "paperly_shimmer_offset"
    )

    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim, translateAnim * 0.25f),
        end = Offset(translateAnim + 360f, translateAnim * 0.25f + 160f)
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    val brush = rememberPaperlyShimmerBrush()
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun ShimmerTextLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Dp = 12.dp,
    shape: Shape = CircleShape
) {
    ShimmerBox(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        shape = shape
    )
}

@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        content()
    }
}

@Composable
fun ShimmerRecentPdfCard(modifier: Modifier = Modifier) {
    ShimmerCard(
        modifier = modifier
            .width(184.dp)
            .testTag("shimmer_recent_pdf_card"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    modifier = Modifier.size(width = 36.dp, height = 46.dp),
                    shape = RoundedCornerShape(8.dp)
                )
                ShimmerBox(
                    modifier = Modifier.size(width = 54.dp, height = 20.dp),
                    shape = CircleShape
                )
            }
            ShimmerTextLine(widthFraction = 0.85f, height = 14.dp)
            ShimmerTextLine(widthFraction = 0.65f, height = 10.dp)
        }
    }
}

@Composable
fun ShimmerRecentImageCard(modifier: Modifier = Modifier) {
    ShimmerCard(
        modifier = modifier
            .width(184.dp)
            .testTag("shimmer_recent_image_card"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp),
                shape = RoundedCornerShape(10.dp)
            )
            ShimmerTextLine(widthFraction = 0.82f, height = 14.dp)
            ShimmerTextLine(widthFraction = 0.70f, height = 10.dp)
        }
    }
}

@Composable
fun ShimmerFileRowCard(modifier: Modifier = Modifier) {
    ShimmerCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("shimmer_file_row_card"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBox(
                modifier = Modifier.size(width = 40.dp, height = 52.dp),
                shape = RoundedCornerShape(10.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShimmerTextLine(widthFraction = 0.78f, height = 14.dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBox(
                        modifier = Modifier.size(width = 52.dp, height = 18.dp),
                        shape = CircleShape
                    )
                    ShimmerBox(
                        modifier = Modifier.size(width = 96.dp, height = 11.dp),
                        shape = CircleShape
                    )
                }
            }
            ShimmerBox(modifier = Modifier.size(28.dp), shape = CircleShape)
        }
    }
}

@Composable
fun ShimmerPreviewCard(
    modifier: Modifier = Modifier,
    previewHeight: Dp = 190.dp
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight),
                shape = RoundedCornerShape(12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ShimmerBox(modifier = Modifier.size(width = 110.dp, height = 12.dp), shape = CircleShape)
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerBox(modifier = Modifier.size(width = 64.dp, height = 12.dp), shape = CircleShape)
            }
        }
    }
}
