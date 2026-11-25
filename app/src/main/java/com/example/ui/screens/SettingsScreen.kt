package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.billing.PaperlyBillingManager
import com.example.core.formatFileSize
import com.example.data.model.CompressionLevel
import com.example.ui.components.FittedSingleLineText
import com.example.ui.components.pressScaleEffect
import com.example.ui.components.rememberPaperlyHaptics
import com.example.ui.theme.PaperlyEmerald

private const val PRIVACY_POLICY_URL = "https://dark2242v2-hue.github.io/paperly-app/privacy-policy.html"
private const val SUPPORT_EMAIL = "joproduction6@gmail.com"

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    isPro: Boolean,
    activePlanProductId: String?,
    isRestoringPurchases: Boolean,
    debugProOverride: Boolean,
    defaultCompression: CompressionLevel,
    savedFileCount: Int,
    tempCacheSizeBytes: Long,
    onToggleDarkMode: () -> Unit,
    onOpenProSheet: () -> Unit,
    onRestorePurchases: () -> Unit,
    onToggleDebugPro: (Boolean) -> Unit,
    onSelectCompression: (CompressionLevel) -> Unit,
    onRefreshCacheSize: () -> Unit,
    onClearTempCache: () -> Unit,
    onClearAllFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberPaperlyHaptics()
    var showClearDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefreshCacheSize()
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear local Paperly files?") },
            text = { Text("This permanently deletes all $savedFileCount processed PDF(s) and saved local images inside Paperly.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllFiles()
                        showClearDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_files_button")
                ) {
                    Text("Clear Files")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms of Service") },
            text = {
                Text(
                    text = "Paperly is developed by JO Production.\n\n" +
                        "• Local Tools: Merge, split, compress, scan, sign, lock, crop, and OCR process documents and photos directly on your device.\n" +
                        "• Optional AI Features: AI Summarize and AI Text-to-Image send only the text or prompt you submit over HTTPS.\n" +
                        "• Paperly Pro: Subscriptions renew automatically via Google Play unless canceled at least 24 hours before renewal.\n" +
                        "• Support: Contact $SUPPORT_EMAIL for help or feedback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(onClick = { showTermsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .graphicsLayer {},
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Card 1: Paperly Pro Status + Restore Purchases
            item {
                val activePlanLabel = when (activePlanProductId) {
                    PaperlyBillingManager.PRODUCT_YEARLY -> "Yearly Pro Plan"
                    PaperlyBillingManager.PRODUCT_MONTHLY -> "Monthly Pro Plan"
                    else -> if (isPro) "Paperly Pro Active" else null
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPro) {
                            PaperlyEmerald.copy(alpha = 0.10f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isPro) PaperlyEmerald.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.testTag("settings_pro_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenProSheet)
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WorkspacePremium,
                                    contentDescription = "Paperly Pro",
                                    tint = if (isPro) PaperlyEmerald else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (isPro) "Paperly Pro Active" else "Upgrade to Paperly Pro",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isPro) {
                                        Surface(
                                            shape = CircleShape,
                                            color = PaperlyEmerald.copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PaperlyEmerald,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (isPro) {
                                        "${activePlanLabel ?: "Pro"} • Ad-free workspace & unlimited AI"
                                    } else {
                                        "3-day free trial • Remove all ads & unlock unlimited AI"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!isPro) {
                            Button(
                                onClick = onOpenProSheet,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_upgrade_pro_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WorkspacePremium,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FittedSingleLineText("View Pro Plans", fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onRestorePurchases,
                            enabled = !isRestoringPurchases,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_restore_purchases_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FittedSingleLineText(
                                text = if (isRestoringPurchases) "Restoring Purchases…" else "Restore Purchases"
                            )
                        }

                        if (BuildConfig.DEBUG) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.BugReport,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text("Debug Pro Override", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Debug builds only — toggle Pro without billing",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = debugProOverride,
                                    onCheckedChange = onToggleDebugPro,
                                    modifier = Modifier.testTag("settings_debug_pro_switch")
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Appearance & Preferences
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Appearance & Preferences",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DarkMode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text("Dark Mode", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = if (isDarkMode) "Midnight slate theme" else "Clean #F8FAFC paper white",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = {
                                    haptics.selectionClick()
                                    onToggleDarkMode()
                                },
                                modifier = Modifier.testTag("settings_dark_mode_switch")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Compress,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Default Compression Preset", style = MaterialTheme.typography.titleSmall)
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CompressionLevel.entries.forEach { level ->
                                    val selected = defaultCompression == level
                                    val pillBg by animateColorAsState(
                                        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        animationSpec = tween(durationMillis = 160, easing = EaseOutCubic),
                                        label = "preset_bg_${level.name}"
                                    )
                                    val pillFg by animateColorAsState(
                                        targetValue = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        animationSpec = tween(durationMillis = 160, easing = EaseOutCubic),
                                        label = "preset_fg_${level.name}"
                                    )
                                    val interactionSource = remember { MutableInteractionSource() }

                                    Surface(
                                        onClick = {
                                            haptics.selectionClick()
                                            onSelectCompression(level)
                                        },
                                        interactionSource = interactionSource,
                                        shape = RoundedCornerShape(10.dp),
                                        color = pillBg,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .pressScaleEffect(interactionSource)
                                            .testTag("settings_compression_${level.name.lowercase()}")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                                        ) {
                                            FittedSingleLineText(
                                                text = level.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = pillFg,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = defaultCompression.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Card 3: Storage & Cache
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Storage & Cache",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = PaperlyEmerald
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("100% On-Device Processing", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Temp cache: ${formatFileSize(tempCacheSizeBytes)} • Saved files: $savedFileCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onClearTempCache,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_clear_cache_button")
                        ) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            FittedSingleLineText("Clear Temp Cache (${formatFileSize(tempCacheSizeBytes)})")
                        }

                        OutlinedButton(
                            onClick = { showClearDialog = true },
                            enabled = savedFileCount > 0,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_clear_files_button")
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            FittedSingleLineText("Clear Saved Files ($savedFileCount)")
                        }
                    }
                }
            }

            // Card 4: Support & Legal
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Support & Legal",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        SettingsActionRow(
                            icon = Icons.Filled.StarRate,
                            title = "Rate on Google Play",
                            subtitle = "Enjoying Paperly? Leave a quick review",
                            testTagName = "settings_rate_row",
                            onClick = {
                                val marketUri = Uri.parse("market://details?id=${context.packageName}")
                                val webUri = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                                val openedMarket = runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, marketUri))
                                }.isSuccess
                                if (!openedMarket) {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                    }.onFailure {
                                        Toast.makeText(context, "Google Play is unavailable on this device.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        SettingsActionRow(
                            icon = Icons.Filled.Share,
                            title = "Share App",
                            subtitle = "Send Paperly PDF & Image Studio to friends",
                            testTagName = "settings_share_row",
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Check out Paperly — a fast, private PDF & Image Studio: https://play.google.com/store/apps/details?id=${context.packageName}"
                                    )
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Paperly"))
                                }
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        SettingsActionRow(
                            icon = Icons.Filled.Policy,
                            title = "Privacy Policy",
                            subtitle = "View our on-device privacy & data policy",
                            testTagName = "settings_privacy_row",
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                                }.onFailure {
                                    Toast.makeText(context, "Could not open browser.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        SettingsActionRow(
                            icon = Icons.Filled.Description,
                            title = "Terms of Service",
                            subtitle = "License, AI usage & subscription terms",
                            testTagName = "settings_terms_row",
                            onClick = { showTermsDialog = true }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        SettingsActionRow(
                            icon = Icons.Filled.Email,
                            title = "Support",
                            subtitle = SUPPORT_EMAIL,
                            testTagName = "settings_support_row",
                            onClick = {
                                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:$SUPPORT_EMAIL")
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        "Paperly v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) Support"
                                    )
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(emailIntent, "Contact JO Production"))
                                }.onFailure {
                                    Toast.makeText(context, "Email: $SUPPORT_EMAIL", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • Paperly by JO Production",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 10.dp)
                        .testTag("settings_version_footer")
                )
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTagName: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTagName),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
