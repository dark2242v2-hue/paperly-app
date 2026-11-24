package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.core.formatFileSize
import com.example.data.local.PdfRecordEntity
import com.example.data.model.PdfToolType
import com.example.ui.theme.PaperlyEmerald
import com.example.ui.viewmodel.PaperlyViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    recordId: Long,
    viewModel: PaperlyViewModel,
    onQuickAction: (PdfToolType, PdfRecordEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var record by remember { mutableStateOf<PdfRecordEntity?>(null) }
    var unlockedRecord by remember { mutableStateOf<PdfRecordEntity?>(null) }
    var isPasswordProtected by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isUnlocking by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(recordId) {
        val loaded = viewModel.loadRecordById(recordId)
        record = loaded
        unlockedRecord = null
        passwordInput = ""
        passwordError = null
        isUnlocking = false
        isPasswordProtected = if (loaded != null) {
            loaded.operationTag == "Locked" || viewModel.pdfEngine.requiresPassword(loaded.filePath)
        } else {
            false
        }
    }

    fun attemptUnlock() {
        val currentRec = record ?: return
        if (isUnlocking) return
        if (passwordInput.isEmpty()) {
            passwordError = "Enter PDF Password"
            return
        }
        isUnlocking = true
        passwordError = null
        coroutineScope.launch {
            try {
                val result = viewModel.pdfEngine.unlockPdfForViewing(currentRec.filePath, passwordInput)
                unlockedRecord = currentRec.copy(
                    filePath = result.unlockedFilePath,
                    pageCount = result.pageCount,
                    operationTag = "Unlocked"
                )
                passwordError = null
            } catch (e: Exception) {
                passwordError = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Incorrect password, please try again"
            } finally {
                isUnlocking = false
            }
        }
    }

    val activeRecord = unlockedRecord ?: record
    val isLockedView = isPasswordProtected && unlockedRecord == null

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val current = record
        if (destUri != null && current != null) {
            viewModel.saveRecordToUri(current.filePath, destUri)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = record?.displayName ?: "PDF Preview",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        activeRecord?.let {
                            val subtitleText = if (isLockedView) {
                                "Encrypted PDF • ${formatFileSize(it.sizeBytes)}"
                            } else {
                                "${it.pageCount} page(s) • ${formatFileSize(it.sizeBytes)}"
                            }
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    record?.let { rec ->
                        IconButton(
                            onClick = { saveLauncher.launch(rec.displayName) },
                            modifier = Modifier.testTag("viewer_download_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Download PDF")
                        }
                        IconButton(
                            onClick = {
                                val shareUri = viewModel.pdfEngine.getShareUri(rec.filePath)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share PDF"))
                            },
                            modifier = Modifier.testTag("viewer_share_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share PDF")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            activeRecord?.let { rec ->
                if (!isLockedView) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    FilledTonalButton(
                                        onClick = { onQuickAction(PdfToolType.AI_SUMMARIZE, rec) },
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("viewer_quick_ai_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AI Summarize")
                                    }
                                }
                                item {
                                    FilledTonalButton(
                                        onClick = { onQuickAction(PdfToolType.COMPRESS, rec) },
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("viewer_quick_compress_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.Compress, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Compress")
                                    }
                                }
                                item {
                                    FilledTonalButton(
                                        onClick = { onQuickAction(PdfToolType.MERGE, rec) },
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("viewer_quick_merge_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Merge")
                                    }
                                }
                                item {
                                    FilledTonalButton(
                                        onClick = { onQuickAction(PdfToolType.LOCK, rec) },
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("viewer_quick_lock_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Lock PDF")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val current = activeRecord
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (isLockedView) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .testTag("viewer_password_card")
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(PaperlyEmerald.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Locked PDF",
                                tint = PaperlyEmerald,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "Password-Protected PDF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enter the document password to unlock and view pages right inside Paperly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                if (passwordError != null) passwordError = null
                            },
                            label = { Text("Enter PDF Password") },
                            singleLine = true,
                            isError = passwordError != null,
                            supportingText = passwordError?.let { err ->
                                {
                                    Text(
                                        text = err,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { attemptUnlock() }
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("viewer_password_visibility_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("viewer_password_input")
                        )

                        Button(
                            onClick = { attemptUnlock() },
                            enabled = !isUnlocking,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("viewer_unlock_button")
                        ) {
                            if (isUnlocking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unlocking…")
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Unlock & View",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(current.pageCount) { pageIndex ->
                        val pageBitmap by produceState<Bitmap?>(initialValue = null, current.filePath, pageIndex) {
                            value = viewModel.pdfEngine.renderPageThumbnail(
                                filePath = current.filePath,
                                pageIndex = pageIndex,
                                maxWidth = 1080
                            )
                        }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column {
                                val bmp = pageBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Page ${pageIndex + 1}",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(340.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "Page ${pageIndex + 1} of ${current.pageCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
