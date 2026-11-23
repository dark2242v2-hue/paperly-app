package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button as AndroidButton
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ads.AdUnitIds
import com.example.ads.PaperlyAdManager
import com.example.data.model.PdfToolType
import com.example.ui.navigation.PaperlyRoutes
import com.example.ui.theme.PaperlyEmerald
import com.example.ui.theme.PaperlyIndigo
import com.example.ui.theme.PaperlyViolet
import com.example.ui.viewmodel.RewardedGateModalState
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun PaperlyTopBar(
    isDarkMode: Boolean,
    isPro: Boolean,
    onToggleDarkMode: () -> Unit,
    onProClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, PaperlyViolet)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "P",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
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
                onClick = onToggleDarkMode,
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

            Surface(
                onClick = onProClick,
                shape = CircleShape,
                color = if (isPro) PaperlyEmerald.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isPro) PaperlyEmerald.copy(alpha = 0.45f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .height(48.dp)
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
fun PaperlyBottomBarWithBanner(
    currentRoute: String?,
    isPro: Boolean,
    showHomeBanner: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Anchored Adaptive AdMob Banner slot on Home (hidden when Paperly Pro is active)
        AnimatedVisibility(
            visible = showHomeBanner && !isPro,
            enter = expandVertically(),
            exit = shrinkVertically()
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
                    onClick = { onNavigate(route) },
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

@Composable
fun RewardedUnlockModal(
    state: RewardedGateModalState,
    onWatchVideo: () -> Unit,
    onUpgradePro: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.visible) return

    val isScanTool = state.targetTool == PdfToolType.SCAN
    val modalTitle = if (isScanTool) {
        stringResource(R.string.scan_modal_title)
    } else {
        stringResource(R.string.ai_modal_title)
    }
    val modalSubtitle = if (isScanTool) {
        stringResource(R.string.scan_modal_subtitle)
    } else {
        stringResource(R.string.ai_modal_subtitle)
    }
    val headerIcon = if (isScanTool) Icons.Filled.DocumentScanner else Icons.Filled.AutoAwesome

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("rewarded_unlock_gate_modal"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = headerIcon,
                                contentDescription = modalTitle,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = modalTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("rewarded_modal_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.ai_modal_skip),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = modalSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                state.errorMessage?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }

                Button(
                    onClick = onWatchVideo,
                    enabled = !state.isLoadingAd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("rewarded_watch_video_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (state.isLoadingAd) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.rewarded_loading_video),
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = stringResource(R.string.ai_modal_watch_video),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = stringResource(R.string.ai_modal_watch_video_sub),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onUpgradePro,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("rewarded_upgrade_pro_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = stringResource(R.string.ai_modal_upgrade_pro),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.ai_modal_upgrade_pro_sub),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("rewarded_modal_skip_button")
                ) {
                    Text(
                        text = stringResource(R.string.ai_modal_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperlyProSheet(
    visible: Boolean,
    isPro: Boolean,
    onTogglePro: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PaperlyIndigo.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = "Paperly Pro",
                        tint = PaperlyIndigo
                    )
                }
                Column {
                    Text(
                        text = "Paperly Pro",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isPro) "Active • Ad-free experience enabled" else "Clean, ad-free PDF studio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            listOf(
                "Zero banner or native sponsored ads",
                "Unlimited instant Gemini AI PDF summaries",
                "Priority local batch processing"
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

            Button(
                onClick = { onTogglePro(!isPro) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("pro_sheet_action_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (isPro) "Switch back to Free (with Ads)" else "Activate Paperly Pro (Ad-Free)",
                    style = MaterialTheme.typography.labelLarge
                )
            }
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
                modifier = Modifier.height(48.dp).testTag("clear_signature_button")
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
    if (strokes.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return null
    val bitmap = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
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
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) {
                    lineTo(pts[i].x, pts[i].y)
                }
            }
            canvas.drawPath(path, paint)
        }
    }
    return bitmap
}
