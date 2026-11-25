package com.example.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Button as AndroidButton
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ads.AdUnitIds
import com.example.ads.PaperlyAdManager
import com.example.billing.BillingUiState
import com.example.billing.PaperlyBillingManager
import com.example.billing.ProPlanType
import com.example.data.model.ImageToolType
import com.example.data.model.PdfToolType
import com.example.ui.navigation.PaperlyRoutes
import com.example.ui.theme.PaperlyEmerald
import com.example.ui.theme.PaperlyIndigo
import com.example.ui.theme.PaperlyViolet
import com.example.ui.viewmodel.ProSheetUiState
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun FittedSingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    minFontSize: TextUnit = 9.sp
) {
    var scaledFontSize by remember(text, style.fontSize) {
        mutableStateOf(
            if (style.fontSize.isSpecified) style.fontSize else 14.sp
        )
    }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        style = style.copy(fontSize = scaledFontSize),
        maxLines = 1,
        softWrap = false,
        overflow = if (scaledFontSize <= minFontSize) TextOverflow.Ellipsis else TextOverflow.Clip,
        onTextLayout = { result ->
            if (result.didOverflowWidth && scaledFontSize > minFontSize) {
                val nextValue = (scaledFontSize.value * 0.90f).coerceAtLeast(minFontSize.value)
                if (nextValue < scaledFontSize.value) {
                    scaledFontSize = nextValue.sp
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bringIntoViewAboveKeyboard(
    bottomClearance: Dp = 140.dp
): Modifier = composed {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val imeBottom = WindowInsets.ime.getBottom(density)
    var isFocused by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    fun buildTargetRect(): Rect {
        val topBufferPx = with(density) { 48.dp.toPx() }
        val clearancePx = with(density) { bottomClearance.toPx() }
        return Rect(
            left = 0f,
            top = -topBufferPx,
            right = boxSize.width.toFloat().coerceAtLeast(1f),
            bottom = (boxSize.height.toFloat() + clearancePx).coerceAtLeast(1f)
        )
    }

    LaunchedEffect(isFocused, imeBottom) {
        if (isFocused) {
            val rect = buildTargetRect()
            bringIntoViewRequester.bringIntoView(rect)
            delay(300L)
            bringIntoViewRequester.bringIntoView(buildTargetRect())
        }
    }

    this
        .onSizeChanged { boxSize = it }
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusEvent { focusState ->
            val nowFocused = focusState.isFocused || focusState.hasFocus
            if (nowFocused && !isFocused) {
                isFocused = true
                coroutineScope.launch {
                    bringIntoViewRequester.bringIntoView(buildTargetRect())
                    delay(300L)
                    if (isFocused) {
                        bringIntoViewRequester.bringIntoView(buildTargetRect())
                    }
                }
            } else if (!nowFocused) {
                isFocused = false
            }
        }
}

@Composable
fun PaperlyTopBar(
    isDarkMode: Boolean,
    isPro: Boolean,
    onToggleDarkMode: () -> Unit,
    onProClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberPaperlyHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PaperlyBrandBadge(
                isDarkMode = isDarkMode,
                modifier = Modifier.testTag("top_bar_app_icon")
            )
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "PDF Studio",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    haptics.selectionClick()
                    onToggleDarkMode()
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dark_mode_toggle")
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = stringResource(R.string.toggle_dark_mode),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val proInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    haptics.selectionClick()
                    onProClick()
                },
                interactionSource = proInteraction,
                shape = CircleShape,
                color = if (isPro) PaperlyEmerald.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isPro) PaperlyEmerald.copy(alpha = 0.45f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .pressScaleEffect(proInteraction)
                    .testTag("pro_badge_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = stringResource(R.string.pro_badge),
                        tint = if (isPro) PaperlyEmerald else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isPro) stringResource(R.string.pro_active_badge) else stringResource(R.string.pro_badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isPro) PaperlyEmerald else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun PaperlyBrandBadge(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(38.dp),
        shape = RoundedCornerShape(11.dp),
        shadowElevation = 3.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isDarkMode) Color.White.copy(alpha = 0.2f) else PaperlyIndigo.copy(alpha = 0.22f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(PaperlyIndigo, PaperlyViolet)
                    )
                )
                .padding(7.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Clean white folded document sheet (slightly left-bottom anchored to leave room for top-right sparkle)
                val docLeft = w * 0.10f
                val docTop = h * 0.16f
                val docRight = w * 0.74f
                val docBottom = h * 0.94f
                val cornerFold = w * 0.22f
                val r = w * 0.10f

                val docPath = Path().apply {
                    moveTo(docLeft + r, docTop)
                    lineTo(docRight - cornerFold, docTop)
                    lineTo(docRight, docTop + cornerFold)
                    lineTo(docRight, docBottom - r)
                    quadraticTo(docRight, docBottom, docRight - r, docBottom)
                    lineTo(docLeft + r, docBottom)
                    quadraticTo(docLeft, docBottom, docLeft, docBottom - r)
                    lineTo(docLeft, docTop + r)
                    quadraticTo(docLeft, docTop, docLeft + r, docTop)
                    close()
                }
                drawPath(path = docPath, color = Color.White)

                // Folded dog-ear flap accent
                val foldPath = Path().apply {
                    moveTo(docRight - cornerFold, docTop)
                    lineTo(docRight - cornerFold, docTop + cornerFold)
                    lineTo(docRight, docTop + cornerFold)
                    close()
                }
                drawPath(path = foldPath, color = PaperlyIndigo.copy(alpha = 0.28f))

                // Crisp horizontal text lines inside the document
                val lineColor = PaperlyIndigo.copy(alpha = 0.45f)
                val lineStroke = (w * 0.08f).coerceAtLeast(1.5f)
                drawLine(
                    color = lineColor,
                    start = Offset(docLeft + w * 0.13f, docTop + h * 0.36f),
                    end = Offset(docRight - w * 0.13f, docTop + h * 0.36f),
                    strokeWidth = lineStroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = lineColor,
                    start = Offset(docLeft + w * 0.13f, docTop + h * 0.54f),
                    end = Offset(docRight - w * 0.22f, docTop + h * 0.54f),
                    strokeWidth = lineStroke,
                    cap = StrokeCap.Round
                )

                // Crisp white 4-point sparkle mark in top-right corner
                val sx = w * 0.80f
                val sy = h * 0.20f
                val sr = w * 0.19f
                val waist = sr * 0.16f
                val sparklePath = Path().apply {
                    moveTo(sx, sy - sr)
                    quadraticTo(sx + waist, sy - waist, sx + sr, sy)
                    quadraticTo(sx + waist, sy + waist, sx, sy + sr)
                    quadraticTo(sx - waist, sy + waist, sx - sr, sy)
                    quadraticTo(sx - waist, sy - waist, sx, sy - sr)
                    close()
                }
                drawPath(path = sparklePath, color = Color.White)
            }
        }
    }
}

@Composable
fun PaperlyBottomBarWithBanner(
    currentRoute: String?,
    isPro: Boolean,
    showHomeBanner: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberPaperlyHaptics()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {}
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Anchored Adaptive AdMob Banner slot on Home (hidden when Paperly Pro is active)
        AnimatedVisibility(
            visible = showHomeBanner && !isPro,
            enter = EnterTransition.None,
            exit = ExitTransition.None
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                AnchoredAdaptiveBannerSlot(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .testTag("home_adaptive_banner_ad")
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars
        ) {
            val items = listOf(
                Triple(PaperlyRoutes.HOME, stringResource(R.string.nav_home), Icons.Filled.Home to Icons.Outlined.Home),
                Triple(PaperlyRoutes.FILES, stringResource(R.string.nav_files), Icons.Filled.Folder to Icons.Outlined.Folder),
                Triple(PaperlyRoutes.SETTINGS, stringResource(R.string.nav_settings), Icons.Filled.Settings to Icons.Outlined.Settings)
            )
            items.forEach { (route, label, icons) ->
                val selected = currentRoute == route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        haptics.selectionClick()
                        onNavigate(route)
                    },
                    icon = {
                        Icon(
                            imageVector = if (selected) icons.first else icons.second,
                            contentDescription = label
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_${label.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun AnchoredAdaptiveBannerSlot(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val adWidthDp = maxWidth.value.toInt().coerceAtLeast(320)
        val adView = remember(context, adWidthDp) {
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
            AdView(context).apply {
                adUnitId = AdUnitIds.bannerAdUnitId
                setAdSize(adSize)
                loadAd(AdRequest.Builder().build())
            }
        }

        DisposableEffect(adView, lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> adView.resume()
                    Lifecycle.Event.ON_PAUSE -> adView.pause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                adView.destroy()
            }
        }

        AndroidView(
            factory = { adView },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SponsoredNativeAdCard(
    adManager: PaperlyAdManager,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    LaunchedEffect(Unit) {
        delay(240L)
        adManager.loadNativeAd(
            onAdLoaded = { loadedAd ->
                nativeAd = loadedAd
            },
            onAdFailed = {
                // Keep Download Ready unobstructed if native ad fails to fill
            }
        )
    }

    DisposableEffect(nativeAd) {
        val current = nativeAd
        onDispose {
            current?.destroy()
        }
    }

    val ad = nativeAd ?: return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sponsored_native_ad_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                LayoutInflater.from(ctx).inflate(R.layout.layout_paperly_native_ad, null, false)
            },
            update = { rootView ->
                val nativeAdView = rootView.findViewById<NativeAdView>(R.id.native_ad_view)
                val badgeView = rootView.findViewById<TextView>(R.id.ad_sponsored_badge)
                val advertiserView = rootView.findViewById<TextView>(R.id.ad_advertiser)
                val iconView = rootView.findViewById<ImageView>(R.id.ad_app_icon)
                val headlineView = rootView.findViewById<TextView>(R.id.ad_headline)
                val bodyView = rootView.findViewById<TextView>(R.id.ad_body)
                val ctaView = rootView.findViewById<AndroidButton>(R.id.ad_call_to_action)

                val density = rootView.resources.displayMetrics.density
                val primaryText = if (isDarkMode) AndroidColor.parseColor("#F1F5F9") else AndroidColor.parseColor("#0F172A")
                val secondaryText = if (isDarkMode) AndroidColor.parseColor("#94A3B8") else AndroidColor.parseColor("#475569")
                val badgeFill = if (isDarkMode) AndroidColor.parseColor("#1E293B") else AndroidColor.parseColor("#FFFFFF")
                val badgeStroke = if (isDarkMode) AndroidColor.parseColor("#334155") else AndroidColor.parseColor("#CBD5E1")

                badgeView.text = rootView.context.getString(R.string.sponsored_badge)
                badgeView.setTextColor(secondaryText)
                badgeView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 6f * density
                    setColor(badgeFill)
                    setStroke((1f * density).toInt(), badgeStroke)
                }

                headlineView.setTextColor(primaryText)
                bodyView.setTextColor(secondaryText)
                advertiserView.setTextColor(secondaryText)

                ctaView.setTextColor(AndroidColor.WHITE)
                ctaView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12f * density
                    setColor(AndroidColor.parseColor("#4F46E5"))
                }

                nativeAdView.headlineView = headlineView
                headlineView.text = ad.headline

                nativeAdView.bodyView = bodyView
                if (!ad.body.isNullOrBlank()) {
                    bodyView.visibility = View.VISIBLE
                    bodyView.text = ad.body
                } else {
                    bodyView.visibility = View.GONE
                }

                nativeAdView.callToActionView = ctaView
                if (!ad.callToAction.isNullOrBlank()) {
                    ctaView.visibility = View.VISIBLE
                    ctaView.text = ad.callToAction
                } else {
                    ctaView.visibility = View.GONE
                }

                nativeAdView.iconView = iconView
                val iconDrawable = ad.icon?.drawable
                if (iconDrawable != null) {
                    iconView.visibility = View.VISIBLE
                    iconView.setImageDrawable(iconDrawable)
                } else {
                    iconView.visibility = View.GONE
                }

                nativeAdView.advertiserView = advertiserView
                if (!ad.advertiser.isNullOrBlank()) {
                    advertiserView.visibility = View.VISIBLE
                    advertiserView.text = ad.advertiser
                } else {
                    advertiserView.visibility = View.GONE
                }

                nativeAdView.setNativeAd(ad)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperlyProSheet(
    state: ProSheetUiState,
    isPro: Boolean,
    activePlanProductId: String?,
    billingState: BillingUiState,
    onSubscribe: (ProPlanType) -> Unit,
    onWatchRewardedVideo: () -> Unit,
    onRestorePurchases: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.visible) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPlan by remember(state.visible) { mutableStateOf(ProPlanType.YEARLY) }

    val triggeredToolLabel = when {
        state.triggeredByTool == PdfToolType.AI_SUMMARIZE -> "Unlock AI Summarize"
        state.triggeredByTool == PdfToolType.SIGN -> "Unlock E-Sign PDF"
        state.triggeredByImageTool == ImageToolType.TEXT_TO_IMAGE -> "Unlock AI Text to Image"
        state.triggeredByImageTool == ImageToolType.UPSCALER -> "Unlock AI Image Upscaler"
        else -> null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isPro) PaperlyEmerald.copy(alpha = 0.14f)
                                else PaperlyIndigo.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WorkspacePremium,
                            contentDescription = "Paperly Pro",
                            tint = if (isPro) PaperlyEmerald else PaperlyIndigo
                        )
                    }
                    Column {
                        Text(
                            text = triggeredToolLabel ?: "Paperly Pro",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isPro) {
                                "Active • Ad-free experience enabled"
                            } else if (triggeredToolLabel != null) {
                                "Get unlimited ad-free access with Paperly Pro"
                            } else {
                                "Ad-free PDF studio & unlimited AI"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pro_sheet_top_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            listOf(
                "Zero banner, native, or rewarded video ads",
                "Unlimited AI Summarize, Text-to-Image, Upscaler & E-Sign",
                "100% private on-device PDF & image processing"
            ).forEach { perk ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = PaperlyEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = perk,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isPro) {
                val activePlanLabel = when (activePlanProductId) {
                    PaperlyBillingManager.PRODUCT_MONTHLY -> "Paperly Pro Monthly (${billingState.monthlyPlan.formattedPrice} / month)"
                    PaperlyBillingManager.PRODUCT_YEARLY -> "Paperly Pro Yearly (${billingState.yearlyPlan.formattedPrice} / year)"
                    else -> "Paperly Pro Active"
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PaperlyEmerald.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, PaperlyEmerald.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = activePlanLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Managed through your Google Play account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        val url = "https://play.google.com/store/account/subscriptions?package=${context.packageName}"
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("manage_subscription_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    FittedSingleLineText(
                        text = "Manage Subscription in Google Play",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("pro_sheet_done_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Done", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                // Yearly Plan Card (selected by default with SAVE 58% + Best Value, crossed-out anchor price, & Just ₹33/month • 3 days free)
                YearlySubscriptionPlanCard(
                    yearlyPrice = "${billingState.yearlyPlan.formattedPrice}/year",
                    crossedOutAnnualPrice = "${billingState.yearlyAnchorFormatted}/yr",
                    monthlyEquivalentLine = if (billingState.yearlyPlan.hasFreeTrial) {
                        "Just ${billingState.yearlyMonthlyEquivalentFormatted}/month • 3 days free"
                    } else {
                        "Just ${billingState.yearlyMonthlyEquivalentFormatted}/month • Billed annually"
                    },
                    savingsBadgeText = "SAVE ${billingState.yearlySavingsPercent}%",
                    selected = selectedPlan == ProPlanType.YEARLY,
                    onClick = { selectedPlan = ProPlanType.YEARLY },
                    modifier = Modifier.testTag("pro_plan_yearly")
                )

                // Monthly Plan Card
                MonthlySubscriptionPlanCard(
                    priceText = "${billingState.monthlyPlan.formattedPrice}/month",
                    selected = selectedPlan == ProPlanType.MONTHLY,
                    onClick = { selectedPlan = ProPlanType.MONTHLY },
                    modifier = Modifier.testTag("pro_plan_monthly")
                )

                val feedbackMessage = state.rewardedError ?: billingState.statusMessage
                feedbackMessage?.let { status ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }

                val isYearlyTrial = selectedPlan == ProPlanType.YEARLY && billingState.yearlyPlan.hasFreeTrial
                val ctaLabel = when {
                    isYearlyTrial -> "Start 3-Day Free Trial"
                    selectedPlan == ProPlanType.YEARLY -> "Continue Yearly • ${billingState.yearlyPlan.formattedPrice}/year"
                    else -> "Continue Monthly • ${billingState.monthlyPlan.formattedPrice}/month"
                }

                Button(
                    onClick = { onSubscribe(selectedPlan) },
                    enabled = !billingState.isPurchasing && !billingState.isRestoring && !state.isLoadingRewardedAd,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .testTag("pro_sheet_action_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (billingState.isPurchasing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        FittedSingleLineText(
                            text = "Opening Google Play…",
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        FittedSingleLineText(
                            text = ctaLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Reassuring trust line directly below 'Start 3-Day Free Trial'
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = PaperlyEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "No payment due today • Cancel anytime in 1 tap on Google Play",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = if (isYearlyTrial) {
                        "Then ${billingState.yearlyPlan.formattedPrice}/year after 3-day free trial"
                    } else if (selectedPlan == ProPlanType.YEARLY) {
                        "Billed ${billingState.yearlyPlan.formattedPrice}/year via Google Play"
                    } else {
                        "Billed ${billingState.monthlyPlan.formattedPrice}/month via Google Play"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Secondary rewarded option when opened via Trigger 1 (PDF or Image gated tools)
                if (state.triggeredByTool != null || state.triggeredByImageTool != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "OR",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    OutlinedButton(
                        onClick = onWatchRewardedVideo,
                        enabled = !state.isLoadingRewardedAd && !billingState.isPurchasing,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .heightIn(min = 52.dp)
                            .testTag("paywall_watch_video_button")
                    ) {
                        if (state.isLoadingRewardedAd) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Loading short video…",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayCircle,
                                contentDescription = "Watch video",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val rewardedLabel = when {
                                state.triggeredByTool == PdfToolType.AI_SUMMARIZE ->
                                    "Watch a short video to unlock 1 summary free"
                                state.triggeredByImageTool == ImageToolType.TEXT_TO_IMAGE ->
                                    "Watch a short video to unlock 1 AI image free"
                                state.triggeredByImageTool == ImageToolType.UPSCALER ->
                                    "Watch a short video to unlock 1 upscale free"
                                else ->
                                    "Watch a short video to unlock this session free"
                            }
                            Text(
                                text = rewardedLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onRestorePurchases,
                            enabled = !billingState.isRestoring && !billingState.isPurchasing,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("pro_sheet_restore_button")
                        ) {
                            FittedSingleLineText(if (billingState.isRestoring) "Restoring…" else "Restore Purchases")
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("pro_sheet_close_button")
                        ) {
                            Text(
                                text = "Not now",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearlySubscriptionPlanCard(
    yearlyPrice: String,
    crossedOutAnnualPrice: String,
    monthlyEquivalentLine: String,
    savingsBadgeText: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberPaperlyHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 160, easing = EaseOutCubic),
        label = "yearly_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(durationMillis = 160, easing = EaseOutCubic),
        label = "yearly_border"
    )

    Card(
        onClick = {
            haptics.selectionClick()
            onClick()
        },
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .pressScaleEffect(interactionSource),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = PaperlyIndigo
                ) {
                    Text(
                        text = "Best Value",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF16A34A)
                ) {
                    Text(
                        text = savingsBadgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = onClick
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Yearly",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = monthlyEquivalentLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = PaperlyEmerald,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = crossedOutAnnualPrice,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough
                    )
                    Text(
                        text = yearlyPrice,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlySubscriptionPlanCard(
    priceText: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberPaperlyHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 160, easing = EaseOutCubic),
        label = "monthly_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(durationMillis = 160, easing = EaseOutCubic),
        label = "monthly_border"
    )

    Card(
        onClick = {
            haptics.selectionClick()
            onClick()
        },
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .pressScaleEffect(interactionSource),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onClick
                )
                Column {
                    Text(
                        text = "Monthly",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Standard monthly billing • Cancel anytime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = priceText,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SignaturePad(
    strokes: List<List<Offset>>,
    onStrokeAdded: (List<Offset>) -> Unit,
    onClear: () -> Unit,
    onSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeStroke = remember { mutableStateListOf<Offset>() }
    val inkColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Draw your signature below",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
                onClick = {
                    activeStroke.clear()
                    onClear()
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("clear_signature_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Clear signature",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .onSizeChanged(onSizeChanged)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            activeStroke.clear()
                            activeStroke.add(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            activeStroke.add(change.position)
                        },
                        onDragEnd = {
                            if (activeStroke.size > 1) {
                                onStrokeAdded(activeStroke.toList())
                            }
                            activeStroke.clear()
                        },
                        onDragCancel = {
                            activeStroke.clear()
                        }
                    )
                }
                .testTag("signature_canvas")
        ) {
            if (strokes.isEmpty() && activeStroke.isEmpty()) {
                Text(
                    text = "Sign here with your finger",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Canvas(modifier = Modifier.matchParentSize()) {
                val allStrokes = strokes + if (activeStroke.isNotEmpty()) listOf(activeStroke.toList()) else emptyList()
                allStrokes.forEach { pts ->
                    if (pts.size >= 2) {
                        val path = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            for (i in 1 until pts.size) {
                                lineTo(pts[i].x, pts[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = inkColor,
                            style = Stroke(
                                width = 6f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

fun renderSignatureBitmap(strokes: List<List<Offset>>, canvasSize: IntSize): Bitmap? {
    val allPoints = strokes.flatten()
    if (allPoints.size < 2 || canvasSize.width <= 0 || canvasSize.height <= 0) return null

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    allPoints.forEach { pt ->
        if (pt.x < minX) minX = pt.x
        if (pt.y < minY) minY = pt.y
        if (pt.x > maxX) maxX = pt.x
        if (pt.y > maxY) maxY = pt.y
    }

    val pad = 14f
    val left = (minX - pad).coerceAtLeast(0f)
    val top = (minY - pad).coerceAtLeast(0f)
    val right = (maxX + pad).coerceAtMost(canvasSize.width.toFloat())
    val bottom = (maxY + pad).coerceAtMost(canvasSize.height.toFloat())
    val outW = (right - left).toInt().coerceAtLeast(24)
    val outH = (bottom - top).toInt().coerceAtLeast(24)

    val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = AndroidPaint().apply {
        color = AndroidColor.parseColor("#0F172A")
        strokeWidth = 8f
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        isAntiAlias = true
    }
    strokes.forEach { pts ->
        if (pts.size >= 2) {
            val path = AndroidPath().apply {
                moveTo(pts.first().x - left, pts.first().y - top)
                for (i in 1 until pts.size) {
                    lineTo(pts[i].x - left, pts[i].y - top)
                }
            }
            canvas.drawPath(path, paint)
        }
    }
    return bitmap
}
