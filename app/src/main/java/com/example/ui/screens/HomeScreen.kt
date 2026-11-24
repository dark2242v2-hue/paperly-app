package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.formatFileSize
import com.example.core.formatTimestamp
import com.example.data.local.PdfRecordEntity
import com.example.data.model.PdfToolType
import com.example.ui.theme.PaperlyAmber
import com.example.ui.theme.PaperlyAmberSoft
import com.example.ui.theme.PaperlyEmerald
import com.example.ui.theme.PaperlyEmeraldSoft
import com.example.ui.theme.PaperlyIndigo
import com.example.ui.theme.PaperlyIndigoTint
import com.example.ui.theme.PaperlyRose
import com.example.ui.theme.PaperlyRoseSoft
import com.example.ui.theme.PaperlySky
import com.example.ui.theme.PaperlySkySoft
import com.example.ui.theme.PaperlyViolet
import com.example.ui.theme.PaperlyVioletSoft

private data class ToolCardSpec(
    val tool: PdfToolType,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val accent: Color,
    val softBg: Color,
    val badge: String? = null
)

@Composable
fun HomeScreen(
    recentRecords: List<PdfRecordEntity>,
    onToolSelected: (PdfToolType) -> Unit,
    onRecentSelected: (PdfRecordEntity) -> Unit,
    onSeeAllFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val toolSpecs = listOf(
        ToolCardSpec(
            tool = PdfToolType.MERGE,
            titleRes = R.string.tool_merge_title,
            descRes = R.string.tool_merge_desc,
            icon = Icons.AutoMirrored.Filled.MergeType,
            accent = PaperlyIndigo,
            softBg = PaperlyIndigoTint
        ),
        ToolCardSpec(
            tool = PdfToolType.SPLIT,
            titleRes = R.string.tool_split_title,
            descRes = R.string.tool_split_desc,
            icon = Icons.AutoMirrored.Filled.CallSplit,
            accent = PaperlySky,
            softBg = PaperlySkySoft
        ),
        ToolCardSpec(
            tool = PdfToolType.COMPRESS,
            titleRes = R.string.tool_compress_title,
            descRes = R.string.tool_compress_desc,
            icon = Icons.Filled.Compress,
            accent = PaperlyEmerald,
            softBg = PaperlyEmeraldSoft
        ),
        ToolCardSpec(
            tool = PdfToolType.SCAN,
            titleRes = R.string.tool_scan_title,
            descRes = R.string.tool_scan_desc,
            icon = Icons.Filled.DocumentScanner,
            accent = PaperlyAmber,
            softBg = PaperlyAmberSoft
        ),
        ToolCardSpec(
            tool = PdfToolType.AI_SUMMARIZE,
            titleRes = R.string.tool_ai_title,
            descRes = R.string.tool_ai_desc,
            icon = Icons.Filled.AutoAwesome,
            accent = PaperlyViolet,
            softBg = PaperlyVioletSoft,
            badge = "AI"
        ),
        ToolCardSpec(
            tool = PdfToolType.SIGN,
            titleRes = R.string.tool_sign_title,
            descRes = R.string.tool_sign_desc,
            icon = Icons.Filled.Draw,
            accent = PaperlyRose,
            softBg = PaperlyRoseSoft
        )
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_headline),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.home_subheadline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Recent PDFs Horizontal Carousel
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.recent_pdfs_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (recentRecords.isNotEmpty()) {
                            TextButton(
                                onClick = onSeeAllFiles,
                                modifier = Modifier.height(48.dp).testTag("recent_see_all_button")
                            ) {
                                Text(stringResource(R.string.see_all))
                            }
                        }
                    }

                    if (recentRecords.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .testTag("recent_empty_card"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_empty_documents),
                                    contentDescription = "Empty documents illustration",
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.recent_empty_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.recent_empty_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.testTag("recent_pdfs_row")
                        ) {
                            items(recentRecords.take(10), key = { it.id }) { record ->
                                RecentPdfCard(
                                    record = record,
                                    onClick = { onRecentSelected(record) }
                                )
                            }
                        }
                    }
                }
            }

            // 6 Tool Cards Grid (2 columns)
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tools_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    toolSpecs.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { spec ->
                                ToolActionCard(
                                    spec = spec,
                                    onClick = { onToolSelected(spec.tool) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 7th Tool: Compact full-width Lock PDF card to keep the 2x3 grid balanced & uncluttered
                    LockPdfQuickCard(
                        onClick = { onToolSelected(PdfToolType.LOCK) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LockPdfQuickCard(
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tool_card_lock"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(PaperlyEmeraldSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.tool_lock_title),
                    tint = PaperlyEmerald,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.tool_lock_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.tool_lock_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RecentPdfCard(
    record: PdfRecordEntity,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(184.dp)
            .testTag("recent_pdf_card_${record.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = record.operationTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = record.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${record.pageCount}p • ${formatFileSize(record.sizeBytes)} • ${formatTimestamp(record.createdAtMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ToolActionCard(
    spec: ToolCardSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(142.dp)
            .testTag("tool_card_${spec.tool.routeArg}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(spec.softBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = stringResource(spec.titleRes),
                        tint = spec.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (spec.badge != null) {
                    Surface(
                        shape = CircleShape,
                        color = spec.accent.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = spec.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = spec.accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(spec.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(spec.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
