package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class DragCorner { NONE, MOVE, TL, TR, BL, BR }

@Composable
fun TransparencyCheckerboardCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cell = 14.dp.toPx().coerceAtLeast(8f)
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val light = Color(0xFFF8FAFC)
        val gray = Color(0xFFE2E8F0)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val color = if ((r + c) % 2 == 0) light else gray
                drawRect(
                    color = color,
                    topLeft = Offset(c * cell, r * cell),
                    size = Size(cell + 0.5f, cell + 0.5f)
                )
            }
        }
    }
}

@Composable
fun InteractiveCropPreview(
    bitmap: Bitmap,
    leftNorm: Float,
    topNorm: Float,
    rightNorm: Float,
    bottomNorm: Float,
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    lockedRatio: Float?,
    onCropChanged: (Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val swapped = ((rotationDegrees % 180) + 180) % 180 != 0
    val displayW = if (swapped) bitmap.height else bitmap.width
    val displayH = if (swapped) bitmap.width else bitmap.height
    val imgAspect = (displayW.toFloat() / displayH.coerceAtLeast(1).toFloat()).coerceIn(0.35f, 2.8f)

    val latestLeft by rememberUpdatedState(leftNorm)
    val latestTop by rememberUpdatedState(topNorm)
    val latestRight by rememberUpdatedState(rightNorm)
    val latestBottom by rememberUpdatedState(bottomNorm)
    val latestRatio by rememberUpdatedState(lockedRatio)
    val latestOnChanged by rememberUpdatedState(onCropChanged)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("crop_preview_container"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF121318),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imgAspect)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1A1C24)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Crop preview image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotationDegrees.toFloat()
                            scaleX = if (flipHorizontal) -1f else 1f
                        }
                )

                var activeHandle by remember { mutableStateOf(DragCorner.NONE) }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(displayW, displayH) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val lPx = latestLeft * w
                                    val tPx = latestTop * h
                                    val rPx = latestRight * w
                                    val bPx = latestBottom * h
                                    val threshold = 48.dp.toPx()

                                    val dTL = hypot(start.x - lPx, start.y - tPx)
                                    val dTR = hypot(start.x - rPx, start.y - tPx)
                                    val dBL = hypot(start.x - lPx, start.y - bPx)
                                    val dBR = hypot(start.x - rPx, start.y - bPx)
                                    val minD = minOf(dTL, dTR, dBL, dBR)

                                    activeHandle = when {
                                        minD <= threshold && abs(minD - dTL) < 0.5f -> DragCorner.TL
                                        minD <= threshold && abs(minD - dTR) < 0.5f -> DragCorner.TR
                                        minD <= threshold && abs(minD - dBL) < 0.5f -> DragCorner.BL
                                        minD <= threshold && abs(minD - dBR) < 0.5f -> DragCorner.BR
                                        start.x in lPx..rPx && start.y in tPx..bPx -> DragCorner.MOVE
                                        else -> DragCorner.NONE
                                    }
                                },
                                onDragEnd = { activeHandle = DragCorner.NONE },
                                onDragCancel = { activeHandle = DragCorner.NONE },
                                onDrag = { change, dragAmount ->
                                    if (activeHandle == DragCorner.NONE) return@detectDragGestures
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val dx = dragAmount.x / w
                                    val dy = dragAmount.y / h

                                    var l = latestLeft
                                    var t = latestTop
                                    var r = latestRight
                                    var b = latestBottom
                                    val minSpan = 0.12f

                                    when (activeHandle) {
                                        DragCorner.MOVE -> {
                                            val spanW = r - l
                                            val spanH = b - t
                                            val nl = (l + dx).coerceIn(0f, 1f - spanW)
                                            val nt = (t + dy).coerceIn(0f, 1f - spanH)
                                            l = nl
                                            t = nt
                                            r = nl + spanW
                                            b = nt + spanH
                                        }
                                        DragCorner.TL -> {
                                            l = (l + dx).coerceIn(0f, r - minSpan)
                                            t = (t + dy).coerceIn(0f, b - minSpan)
                                            latestRatio?.let { ratio ->
                                                val desiredNormH = ((r - l) * imgAspect / ratio).coerceIn(minSpan, b)
                                                t = (b - desiredNormH).coerceIn(0f, b - minSpan)
                                            }
                                        }
                                        DragCorner.TR -> {
                                            r = (r + dx).coerceIn(l + minSpan, 1f)
                                            t = (t + dy).coerceIn(0f, b - minSpan)
                                            latestRatio?.let { ratio ->
                                                val desiredNormH = ((r - l) * imgAspect / ratio).coerceIn(minSpan, b)
                                                t = (b - desiredNormH).coerceIn(0f, b - minSpan)
                                            }
                                        }
                                        DragCorner.BL -> {
                                            l = (l + dx).coerceIn(0f, r - minSpan)
                                            b = (b + dy).coerceIn(t + minSpan, 1f)
                                            latestRatio?.let { ratio ->
                                                val desiredNormH = ((r - l) * imgAspect / ratio).coerceIn(minSpan, 1f - t)
                                                b = (t + desiredNormH).coerceIn(t + minSpan, 1f)
                                            }
                                        }
                                        DragCorner.BR -> {
                                            r = (r + dx).coerceIn(l + minSpan, 1f)
                                            b = (b + dy).coerceIn(t + minSpan, 1f)
                                            latestRatio?.let { ratio ->
                                                val desiredNormH = ((r - l) * imgAspect / ratio).coerceIn(minSpan, 1f - t)
                                                b = (t + desiredNormH).coerceIn(t + minSpan, 1f)
                                            }
                                        }
                                        DragCorner.NONE -> Unit
                                    }
                                    latestOnChanged(l, t, r, b)
                                }
                            )
                        }
                ) {
                    val l = latestLeft * size.width
                    val t = latestTop * size.height
                    val r = latestRight * size.width
                    val b = latestBottom * size.height
                    val boxW = (r - l).coerceAtLeast(1f)
                    val boxH = (b - t).coerceAtLeast(1f)

                    val scrimColor = Color.Black.copy(alpha = 0.56f)
                    // Top, Bottom, Left, Right scrim strips
                    drawRect(scrimColor, Offset.Zero, Size(size.width, t))
                    drawRect(scrimColor, Offset(0f, b), Size(size.width, size.height - b))
                    drawRect(scrimColor, Offset(0f, t), Size(l, boxH))
                    drawRect(scrimColor, Offset(r, t), Size(size.width - r, boxH))

                    // Crop rectangle border
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(l, t),
                        size = Size(boxW, boxH),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Rule of thirds gridlines
                    val gridColor = Color.White.copy(alpha = 0.42f)
                    for (i in 1..2) {
                        val x = l + boxW * (i / 3f)
                        val y = t + boxH * (i / 3f)
                        drawLine(gridColor, Offset(x, t), Offset(x, b), strokeWidth = 1.dp.toPx())
                        drawLine(gridColor, Offset(l, y), Offset(r, y), strokeWidth = 1.dp.toPx())
                    }

                    // 4 draggable corner handles
                    val handleRadius = 8.dp.toPx()
                    listOf(
                        Offset(l, t),
                        Offset(r, t),
                        Offset(l, b),
                        Offset(r, b)
                    ).forEach { corner ->
                        drawCircle(color = Color(0xFF4F46E5), radius = handleRadius + 2.dp.toPx(), center = corner)
                        drawCircle(color = Color.White, radius = handleRadius, center = corner)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomColorPickerDialog(
    initialColorArgb: Int,
    onColorConfirmed: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableFloatStateOf(AndroidColor.red(initialColorArgb).toFloat()) }
    var green by remember { mutableFloatStateOf(AndroidColor.green(initialColorArgb).toFloat()) }
    var blue by remember { mutableFloatStateOf(AndroidColor.blue(initialColorArgb).toFloat()) }

    val currentColor = Color(
        red = red.roundToInt().coerceIn(0, 255),
        green = green.roundToInt().coerceIn(0, 255),
        blue = blue.roundToInt().coerceIn(0, 255)
    )
    val currentArgb = currentColor.toArgb()
    var hexText by remember(currentArgb) {
        mutableStateOf(String.format("#%06X", 0xFFFFFF and currentArgb))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Background Color", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(currentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { raw ->
                            val sanitized = raw.uppercase().take(7)
                            hexText = sanitized
                            if (Regex("^#[0-9A-F]{6}$").matches(sanitized)) {
                                val parsed = AndroidColor.parseColor(sanitized)
                                red = AndroidColor.red(parsed).toFloat()
                                green = AndroidColor.green(parsed).toFloat()
                                blue = AndroidColor.blue(parsed).toFloat()
                            }
                        },
                        label = { Text("Hex Code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                ColorChannelSlider("R", red, Color(0xFFEF4444)) { red = it }
                ColorChannelSlider("G", green, Color(0xFF10B981)) { green = it }
                ColorChannelSlider("B", blue, Color(0xFF3B82F6)) { blue = it }
            }
        },
        confirmButton = {
            Button(onClick = { onColorConfirmed(currentArgb) }) {
                Text("Apply Color")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ColorChannelSlider(
    label: String,
    value: Float,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.roundToInt().toString(),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun ColorSwatchCircle(
    color: Color?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (color == null) {
                TransparencyCheckerboardCanvas(modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
