package com.clipvault.android.ui.compose

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.ui.viewmodel.ClipboardIntent
import com.clipvault.android.ui.viewmodel.ClipboardUiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val VaultDarkBg = Color(0xFF090D16)
private val VaultCardBg = Color(0xFF111C33)
private val VaultCardBorder = Color(0xFF1E293B)
private val VaultSecurityEmerald = Color(0xFF10B981)
private val VaultSecurityCyan = Color(0xFF38BDF8)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    uiState: ClipboardUiState,
    onIntent: (ClipboardIntent) -> Unit,
    onRequestUnlock: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val vaultListState = rememberLazyListState()
    var searchQuery by remember { mutableStateOf("") }
    var editingVaultClip by remember { mutableStateOf<ClipEntity?>(null) }

    // Security Guard: Auto-dismiss the Vault Edit Dialog if the Vault becomes locked
    LaunchedEffect(uiState.isVaultUnlocked) {
        if (!uiState.isVaultUnlocked) {
            editingVaultClip = null
        }
    }

    // Intercept hardware back button to return to ClipboardScreen
    BackHandler(onBack = onNavigateBack)

    val filteredClips = remember(uiState.vaultClips, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.vaultClips
        } else {
            uiState.vaultClips.filter {
                it.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        containerColor = VaultDarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    VaultSecurityEmerald.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .border(1.dp, VaultSecurityEmerald.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = VaultSecurityEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Encrypted Vault",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "AES-256-GCM · Keystore Protected",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = VaultSecurityEmerald,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Clipboard")
                    }
                },
                actions = {
                    if (uiState.isVaultUnlocked) {
                        FilledTonalButton(
                            onClick = {
                                onIntent(ClipboardIntent.LockVault)
                                onNavigateBack()
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                                contentColor = Color(0xFFFCA5A5)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Lock", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        }
    ) { padding ->
        if (!uiState.isVaultUnlocked) {
            // Locked State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = VaultCardBg),
                    border = BorderStroke(1.dp, VaultCardBorder)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    VaultSecurityEmerald.copy(alpha = 0.15f),
                                    CircleShape
                                )
                                .border(2.dp, VaultSecurityEmerald.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Lock",
                                modifier = Modifier.size(40.dp),
                                tint = VaultSecurityEmerald
                            )
                        }

                        Text(
                            text = "Vault is Locked",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )

                        Text(
                            text = "Clips stored here are encrypted with hardware-backed AES-256-GCM. Please authenticate with biometric sensor or device PIN to access.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Button(
                            onClick = onRequestUnlock,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VaultSecurityEmerald,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Unlock with Biometrics", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Back to Clipboard")
                        }
                    }
                }
            }
        } else {
            // Unlocked State Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Security Notice Banner
                Surface(
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = VaultSecurityEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Zero-Memory Policy: Plaintext is kept in volatile RAM only while viewing and purged on lock.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCBD5E1),
                            maxLines = 2
                        )
                    }
                }

                // Search Bar inside Vault
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = {
                        Text(
                            "Search encrypted vault...",
                            color = Color(0xFF64748B),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = VaultSecurityEmerald
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VaultCardBg,
                        unfocusedContainerColor = VaultCardBg,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = VaultSecurityEmerald,
                        unfocusedBorderColor = VaultCardBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                if (filteredClips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0xFF1E293B), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.EnhancedEncryption,
                                    contentDescription = null,
                                    tint = VaultSecurityCyan,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching encrypted clips" else "Your Vault is Empty",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "Try a different search term" else "Move sensitive passwords, crypto keys, or confidential notes here by tapping the Shield Lock icon on any clip.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = onNavigateBack,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B),
                                    contentColor = VaultSecurityCyan
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Back to Clipboard")
                            }
                        }
                    }
                } else {
                    val showFastScrollTop by remember {
                        derivedStateOf { vaultListState.firstVisibleItemIndex > 2 }
                    }

                    // Properly bounded Box with weight(1f) for smooth scrolling LazyColumn + Visible Vertical Scrollbar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyColumn(
                            state = vaultListState,
                            flingBehavior = ScrollableDefaults.flingBehavior(),
                            modifier = Modifier
                                .fillMaxSize()
                                .drawVerticalScrollbar(
                                    state = vaultListState,
                                    width = 4.dp,
                                    thumbColor = VaultSecurityEmerald,
                                    trackColor = Color(0xFF1E293B).copy(alpha = 0.65f)
                                ),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 84.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = filteredClips,
                                key = { it.id },
                                contentType = { it.category }
                            ) { clip ->
                                Box(modifier = Modifier.animateItemPlacement()) {
                                    VaultClipCard(
                                        clip = clip,
                                        searchQuery = searchQuery,
                                        onCopy = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            clipboardManager.setText(AnnotatedString(clip.content))
                                            Toast.makeText(context, "Encrypted clip copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        onEdit = {
                                            editingVaultClip = clip
                                        },
                                        onUnlockToHistory = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onIntent(ClipboardIntent.ToggleVault(clip.id, true, clip.content))
                                            Toast.makeText(context, "Removed from vault & moved to main clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        onDelete = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onIntent(ClipboardIntent.DeleteClip(clip.id))
                                        }
                                    )
                                }
                            }
                        }

                        // Fast-Scroller "Top" Pill Indicator inside Secure Vault when scrolled down
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showFastScrollTop,
                            enter = fadeIn() + slideInVertically { it },
                            exit = fadeOut() + slideOutVertically { it },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 18.dp)
                        ) {
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    coroutineScope.launch {
                                        vaultListState.animateScrollToItem(0)
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF065F46),
                                border = BorderStroke(1.dp, VaultSecurityEmerald.copy(alpha = 0.6f)),
                                shadowElevation = 6.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Scroll to Top",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Top",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Unified Enlarged Edit Dialog inside Secure Vault (Re-encrypts with AES-256-GCM on save)
    if (editingVaultClip != null && uiState.isVaultUnlocked) {
        val targetVaultClip = editingVaultClip!!
        EditClipDialog(
            clip = targetVaultClip,
            isVaultClip = true,
            onDismiss = { editingVaultClip = null },
            onSave = { updatedPlaintext ->
                onIntent(ClipboardIntent.EditClip(targetVaultClip.id, updatedPlaintext))
                editingVaultClip = null
                Toast.makeText(context, "Encrypted vault clip updated", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun VaultClipCard(
    clip: ClipEntity,
    searchQuery: String = "",
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onUnlockToHistory: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMasked by remember { mutableStateOf(false) }
    val timeFormatted = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(clip.createdAt))

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = VaultCardBg),
        border = BorderStroke(1.dp, VaultSecurityEmerald.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Category, Timestamp, Security Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = VaultSecurityEmerald.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = clip.category.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = VaultSecurityEmerald,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        tint = VaultSecurityEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "AES-256",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = VaultSecurityEmerald
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body Content with Search Keyword Highlighting
            val displayAnnotated = remember(clip.content, isMasked, searchQuery) {
                if (isMasked) {
                    AnnotatedString("•".repeat(minOf(clip.content.length, 32)))
                } else {
                    buildHighlightedClipText(
                        fullText = clip.content,
                        searchQuery = searchQuery,
                        highlightBackground = Color(0xFFFACC15),
                        highlightTextColor = Color(0xFF0F172A)
                    )
                }
            }

            Text(
                text = displayAnnotated,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                color = Color.White,
                maxLines = if (isMasked) 1 else 6,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Eye toggle for masking password
                IconButton(
                    onClick = { isMasked = !isMasked },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isMasked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isMasked) "Show" else "Hide",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy
                    IconButton(
                        onClick = onCopy,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = VaultSecurityEmerald.copy(alpha = 0.15f),
                            contentColor = VaultSecurityEmerald
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                    }

                    // Edit Encrypted Clip
                    IconButton(
                        onClick = onEdit,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF6366F1).copy(alpha = 0.18f),
                            contentColor = Color(0xFF818CF8)
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit Encrypted Clip", modifier = Modifier.size(18.dp))
                    }

                    // Unlock / Move to History
                    IconButton(
                        onClick = onUnlockToHistory,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color(0xFF38BDF8)
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = "Move to History", modifier = Modifier.size(18.dp))
                    }

                    // Delete
                    IconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF7F1D1D).copy(alpha = 0.3f),
                            contentColor = Color(0xFFF87171)
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
