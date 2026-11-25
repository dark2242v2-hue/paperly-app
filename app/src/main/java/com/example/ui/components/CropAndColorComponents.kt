package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.model.BgBrushPoint
import com.example.data.model.BgBrushStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
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
    val transformedBitmap by produceState(
        initialValue = bitmap,
        bitmap,
        rotationDegrees,
        flipHorizontal
    ) {
        val normRot = ((rotationDegrees % 360) + 360) % 360
        value = if (normRot == 0 && !flipHorizontal) {
            bitmap
        } else {
            withContext(Dispatchers.Default) {
                val m = AndroidMatrix().apply {
                    if (normRot != 0) postRotate(normRot.toFloat())
                    if (flipHorizontal) postScale(-1f, 1f)
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            }
        }
    }
    val displayW = transformedBitmap.width
    val displayH = transformedBitmap.height
    val imgAspect = displayW.toFloat() / displayH.coerceAtLeast(1).toFloat()

    val latestLeft by rememberUpdatedState(leftNorm)
    val latestTop by rememberUpdatedState(topNorm)
    val latestRight by rememberUpdatedState(rightNorm)
    val latestBottom by rememberUpdatedState(bottomNorm)
    val latestRatio by rememberUpdatedState(lockedRatio)
    val latestOnChanged by rememberUpdatedState(onCropChanged)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .testTag("crop_preview_container"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF121318),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 170.dp, max = 276.dp)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val availW = maxWidth
            val availH = min(maxHeight.value, 256f).dp
            val fitW: androidx.compose.ui.unit.Dp
            val fitH: androidx.compose.ui.unit.Dp
            if (availW / availH > imgAspect) {
                fitH = availH
                fitW = (availH * imgAspect).coerceAtLeast(48.dp)
            } else {
                fitW = availW
                fitH = (availW / imgAspect).coerceAtLeast(48.dp)
            }

            Box(
                modifier = Modifier
                    .size(width = fitW, height = fitH)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A1C24)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = transformedBitmap.asImageBitmap(),
                    contentDescription = "Crop preview image",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )

                var activeHandle by remember { mutableStateOf(DragCorner.NONE) }
                val touchThresholdPx = with(density) { 44.dp.toPx() }
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

                                    val dTL = hypot(start.x - lPx, start.y - tPx)
                                    val dTR = hypot(start.x - rPx, start.y - tPx)
                                    val dBL = hypot(start.x - lPx, start.y - bPx)
                                    val dBR = hypot(start.x - rPx, start.y - bPx)
                                    val minD = minOf(dTL, dTR, dBL, dBR)

                                    activeHandle = when {
                                        minD <= touchThresholdPx && abs(minD - dTL) < 0.5f -> DragCorner.TL
                                        minD <= touchThresholdPx && abs(minD - dTR) < 0.5f -> DragCorner.TR
                                        minD <= touchThresholdPx && abs(minD - dBL) < 0.5f -> DragCorner.BL
                                        minD <= touchThresholdPx && abs(minD - dBR) < 0.5f -> DragCorner.BR
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
                                    val minSpan = 0.14f

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
                                                val maxSpanW = (b * ratio / imgAspect).coerceAtMost(r)
                                                val clampedSpanW = (r - l).coerceIn(minSpan, maxSpanW.coerceAtLeast(minSpan))
                                                l = (r - clampedSpanW).coerceIn(0f, r - minSpan)
                                                val desiredNormH = ((r - l) * imgAspect / ratio).coerceIn(minSpan, b)
                                                t = (b - desiredNormH).coerceIn(0f, b - minSpan)
                                            }
                                        }
                                        DragCorner.TR -> {
                                            r = (r + dx).coerceIn(l + minSpan, 1f)
                                            t = (t + dy).coerceIn(0f, b - minSpan)
                                            latestRatio?.let { ratio ->
                                                val maxSpanW = (b * ratio / imgAspect).coerceAtMost(1f - l)
                                                val clampedSpanW = (r - l).coerceIn(minSpan, maxSpanW.coerceAtLeast(minSpan))
                                                r = (l + clampedSpanW).coerceIn(l + minSpan, 1f)
                                                val desiredNormH = ((r - l) * imgAspect / ratio).coerceIn(minSpan, b)
                                                t = (b - desiredNormH).coerceIn(0f, b - minSpan)
                                            }
                                        }
                                        DragCorner.BL -> {
                                            l = (l + dx).coerceIn(0f, r - minSpan)
                                            b = (b + dy).coerceIn(t + minSpan, 1f)
                                            latestRatio?.let { ratio ->
                                                val maxSpanW = ((1f - t) * ratio / imgAspect).coerceAtMost(r)
                                                val clampedSpanW = (r - l).coerceIn(minSpan, maxSpanW.coerceAtLeast(minSpan))
                                                l = (r - clampedSpanW).coerceIn(0f, r - minSpan)
                                                val desiredNormH = ((r - l) * imgAspect / ratio).coerceIn(minSpan, 1f - t)
                                                b = (t + desiredNormH).coerceIn(t + minSpan, 1f)
                                            }
                                        }
                                        DragCorner.BR -> {
                                            r = (r + dx).coerceIn(l + minSpan, 1f)
                                            b = (b + dy).coerceIn(t + minSpan, 1f)
                                            latestRatio?.let { ratio ->
                                                val maxSpanW = ((1f - t) * ratio / imgAspect).coerceAtMost(1f - l)
                                                val clampedSpanW = (r - l).coerceIn(minSpan, maxSpanW.coerceAtLeast(minSpan))
                                                r = (l + clampedSpanW).coerceIn(l + minSpan, 1f)
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

                    val scrimColor = Color.Black.copy(alpha = 0.58f)
                    drawRect(scrimColor, Offset.Zero, Size(size.width, t))
                    drawRect(scrimColor, Offset(0f, b), Size(size.width, size.height - b))
                    drawRect(scrimColor, Offset(0f, t), Size(l, boxH))
                    drawRect(scrimColor, Offset(r, t), Size(size.width - r, boxH))

                    drawRect(
                        color = Color.White,
                        topLeft = Offset(l, t),
                        size = Size(boxW, boxH),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    val gridColor = Color.White.copy(alpha = 0.42f)
                    for (i in 1..2) {
                        val x = l + boxW * (i / 3f)
                        val y = t + boxH * (i / 3f)
                        drawLine(gridColor, Offset(x, t), Offset(x, b), strokeWidth = 1.dp.toPx())
                        drawLine(gridColor, Offset(l, y), Offset(r, y), strokeWidth = 1.dp.toPx())
                    }

                    // Bold corner drag handles + L-brackets for unmistakably clear grab targets
                    val handleRadius = 9.dp.toPx()
                    listOf(
                        Offset(l, t),
                        Offset(r, t),
                        Offset(l, b),
                        Offset(r, b)
                    ).forEach { corner ->
                        drawCircle(color = Color(0xFF4F46E5), radius = handleRadius + 2.5.dp.toPx(), center = corner)
                        drawCircle(color = Color.White, radius = handleRadius, center = corner)
                    }
                }
            }
        }
    }
}

@Composable
fun BgTouchUpPreview(
    cutoutBitmap: Bitmap?,
    originalBitmap: Bitmap?,
    selectedBgColorArgb: Int?,
    strokes: List<BgBrushStroke>,
    isEraseMode: Boolean,
    brushRadiusNorm: Float,
    onStrokeCompleted: (BgBrushStroke) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeStrokePoints = remember { mutableStateListOf<BgBrushPoint>() }
    var cursorOffset by remember { mutableStateOf<Offset?>(null) }

    val latestErase by rememberUpdatedState(isEraseMode)
    val latestRadius by rememberUpdatedState(brushRadiusNorm)
    val latestOnComplete by rememberUpdatedState(onStrokeCompleted)

    // Render cutout + committed strokes off the UI thread so dragging stays silky smooth
    val composedPreview by produceState<Bitmap?>(
        initialValue = cutoutBitmap,
        cutoutBitmap,
        originalBitmap,
        strokes
    ) {
        val base = cutoutBitmap
        if (base == null) {
            value = null
            return@produceState
        }
        if (strokes.isEmpty()) {
            value = base
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            val working = base.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = AndroidCanvas(working)
            val w = working.width.toFloat()
            val h = working.height.toFloat()
            val minSide = min(w, h)
            val alignedOrig = if (strokes.any { !it.isErase } && originalBitmap != null) {
                if (originalBitmap.width == working.width && originalBitmap.height == working.height) {
                    originalBitmap
                } else {
                    Bitmap.createScaledBitmap(originalBitmap, working.width, working.height, true)
                }
            } else null

            val shader = alignedOrig?.let { orig ->
                BitmapShader(orig, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }

            try {
                strokes.forEach { stroke ->
                    if (stroke.points.isEmpty()) return@forEach
                    val rPx = (stroke.radiusNorm * minSide).coerceAtLeast(2f)
                    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = rPx * 2f
                        strokeCap = AndroidPaint.Cap.ROUND
                        strokeJoin = AndroidPaint.Join.ROUND
                        if (stroke.isErase) {
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                        } else if (shader != null) {
                            this.shader = shader
                        }
                    }
                    if (!stroke.isErase && shader == null) return@forEach
                    if (stroke.points.size == 1) {
                        val p = stroke.points[0]
                        paint.style = AndroidPaint.Style.FILL
                        canvas.drawCircle(p.xNorm * w, p.yNorm * h, rPx, paint)
                    } else {
                        val path = AndroidPath().apply {
                            val first = stroke.points.first()
                            moveTo(first.xNorm * w, first.yNorm * h)
                            for (i in 1 until stroke.points.size) {
                                val pt = stroke.points[i]
                                lineTo(pt.xNorm * w, pt.yNorm * h)
                            }
                        }
                        canvas.drawPath(path, paint)
                    }
                }
            } finally {
                if (alignedOrig != null && alignedOrig !== originalBitmap && !alignedOrig.isRecycled) {
                    alignedOrig.recycle()
                }
            }
            working
        }
    }

    val displayAspect = remember(cutoutBitmap, originalBitmap) {
        val b = cutoutBitmap ?: originalBitmap
        if (b != null) (b.width.toFloat() / max(1, b.height).toFloat()).coerceIn(0.45f, 2.2f) else 1f
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 210.dp, max = 270.dp)
            .testTag("bg_touchup_preview")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 210.dp, max = 270.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val availW = maxWidth
            val availH = min(maxHeight.value, 254f).dp
            val fitW: androidx.compose.ui.unit.Dp
            val fitH: androidx.compose.ui.unit.Dp
            if (availW / availH > displayAspect) {
                fitH = availH
                fitW = availH * displayAspect
            } else {
                fitW = availW
                fitH = availW / displayAspect
            }

            Box(
                modifier = Modifier
                    .size(width = fitW, height = fitH)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (selectedBgColorArgb == null) {
                    TransparencyCheckerboardCanvas(modifier = Modifier.fillMaxSize())
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(selectedBgColorArgb))
                    )
                }

                val bmp = composedPreview ?: originalBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Subject cutout with touch-up preview",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Touch-up gesture overlay (only active when cutout is ready)
                if (cutoutBitmap != null) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(cutoutBitmap) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        activeStrokePoints.clear()
                                        val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        activeStrokePoints.add(BgBrushPoint(nx, ny))
                                        cursorOffset = offset
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val pos = change.position
                                        val nx = (pos.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (pos.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        activeStrokePoints.add(BgBrushPoint(nx, ny))
                                        cursorOffset = pos
                                    },
                                    onDragEnd = {
                                        if (activeStrokePoints.isNotEmpty()) {
                                            latestOnComplete(
                                                BgBrushStroke(
                                                    isErase = latestErase,
                                                    radiusNorm = latestRadius,
                                                    points = activeStrokePoints.toList()
                                                )
                                            )
                                        }
                                        activeStrokePoints.clear()
                                        cursorOffset = null
                                    },
                                    onDragCancel = {
                                        activeStrokePoints.clear()
                                        cursorOffset = null
                                    }
                                )
                            }
                    ) {
                        if (activeStrokePoints.isNotEmpty()) {
                            val rPx = (latestRadius * min(size.width, size.height)).coerceAtLeast(2f)
                            val strokeColor = if (latestErase) {
                                Color(0xFFEF4444).copy(alpha = 0.55f)
                            } else {
                                Color(0xFF10B981).copy(alpha = 0.55f)
                            }
                            if (activeStrokePoints.size == 1) {
                                val pt = activeStrokePoints.first()
                                drawCircle(
                                    color = strokeColor,
                                    radius = rPx,
                                    center = Offset(pt.xNorm * size.width, pt.yNorm * size.height)
                                )
                            } else {
                                val livePath = Path().apply {
                                    val first = activeStrokePoints.first()
                                    moveTo(first.xNorm * size.width, first.yNorm * size.height)
                                    for (i in 1 until activeStrokePoints.size) {
                                        val pt = activeStrokePoints[i]
                                        lineTo(pt.xNorm * size.width, pt.yNorm * size.height)
                                    }
                                }
                                drawPath(
                                    path = livePath,
                                    color = strokeColor,
                                    style = Stroke(
                                        width = rPx * 2f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                        cursorOffset?.let { pos ->
                            val rPx = latestRadius * min(size.width, size.height)
                            drawCircle(
                                color = if (latestErase) Color(0xFFEF4444) else Color(0xFF10B981),
                                radius = rPx,
                                center = pos,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PassportFramingPreview(
    bitmap: Bitmap?,
    removeBgEnabled: Boolean,
    bgColorArgb: Int,
    zoom: Float,
    offsetXNorm: Float,
    offsetYNorm: Float,
    onFramingChanged: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestZoom by rememberUpdatedState(zoom)
    val latestOffsetX by rememberUpdatedState(offsetXNorm)
    val latestOffsetY by rememberUpdatedState(offsetYNorm)
    val latestOnChanged by rememberUpdatedState(onFramingChanged)

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        modifier = modifier
            .width(126.dp)
            .aspectRatio(35f / 45f)
            .testTag("passport_framing_preview")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (removeBgEnabled) Color(bgColorArgb) else Color.White)
                .clipToBounds()
                .pointerInput(bitmap) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val newZoom = (latestZoom * gestureZoom).coerceIn(0.65f, 3.2f)
                        val newOx = (latestOffsetX + pan.x / w).coerceIn(-0.65f, 0.65f)
                        val newOy = (latestOffsetY + pan.y / h).coerceIn(-0.65f, 0.65f)
                        latestOnChanged(newOx, newOy, newZoom)
                    }
                }
        ) {
            if (bitmap != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tileW = size.width
                    val tileH = size.height
                    val baseScale = max(tileW / max(1, bitmap.width), tileH / max(1, bitmap.height))
                    val effectiveScale = baseScale * latestZoom.coerceIn(0.65f, 3.2f)
                    val drawW = bitmap.width * effectiveScale
                    val drawH = bitmap.height * effectiveScale
                    val left = (tileW - drawW) / 2f + latestOffsetX * tileW
                    val top = (tileH - drawH) * 0.25f + latestOffsetY * tileH

                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1))
                    )
                }
            }
        }
    }
}

@Composable
fun SplitBeforeAfterComparisonPreview(
    originalBitmap: Bitmap?,
    enhancedBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    var zoom2x by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = Color(0xFF121318),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .testTag("upscaler_split_preview")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            splitFraction = (change.position.x / w).coerceIn(0.08f, 0.92f)
                        }
                    }
            ) {
                // Full Enhanced on Right
                if (enhancedBitmap != null) {
                    Image(
                        bitmap = enhancedBitmap.asImageBitmap(),
                        contentDescription = "Enhanced upscaled image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = if (zoom2x) 2.2f else 1f
                                scaleX = s
                                scaleY = s
                            }
                    )
                }

                // Original clipped to Left of splitFraction
                if (originalBitmap != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val splitX = size.width * splitFraction
                        clipRect(left = 0f, top = 0f, right = splitX, bottom = size.height) {
                            val imgW = originalBitmap.width.toFloat().coerceAtLeast(1f)
                            val imgH = originalBitmap.height.toFloat().coerceAtLeast(1f)
                            val fitScale = min(size.width / imgW, size.height / imgH) * (if (zoom2x) 2.2f else 1f)
                            val drawW = imgW * fitScale
                            val drawH = imgH * fitScale
                            val left = (size.width - drawW) / 2f
                            val top = (size.height - drawH) / 2f
                            drawImage(
                                image = originalBitmap.asImageBitmap(),
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1))
                            )
                        }
                    }
                }

                // Divider line + handle
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val splitX = size.width * splitFraction
                    drawLine(
                        color = Color.White,
                        start = Offset(splitX, 0f),
                        end = Offset(splitX, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawCircle(
                        color = Color(0xFF4F46E5),
                        radius = 13.dp.toPx(),
                        center = Offset(splitX, size.height / 2f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 10.dp.toPx(),
                        center = Offset(splitX, size.height / 2f)
                    )
                }

                // Labels
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Original",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF4F46E5).copy(alpha = 0.90f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Enhanced",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CompareArrows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Drag slider to compare clarity",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = if (zoom2x) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { zoom2x = !zoom2x }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (zoom2x) Icons.Filled.ZoomOut else Icons.Filled.ZoomIn,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (zoom2x) "1x Fit" else "2x Detail Zoom",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
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
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                        modifier = Modifier
                            .weight(1f)
                            .bringIntoViewAboveKeyboard()
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
