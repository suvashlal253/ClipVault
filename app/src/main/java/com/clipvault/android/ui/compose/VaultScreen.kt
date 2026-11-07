package com.clipvault.android.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.clipvault.android.ui.viewmodel.ClipboardIntent
import com.clipvault.android.ui.viewmodel.ClipboardUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    uiState: ClipboardUiState,
    onIntent: (ClipboardIntent) -> Unit,
    onRequestUnlock: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encrypted Vault (AES-256)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isVaultUnlocked) {
                        IconButton(onClick = { onIntent(ClipboardIntent.LockVault) }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Now")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (!uiState.isVaultUnlocked) {
            // Locked State Placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometric Lock",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Vault Locked",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Clips inside are protected with AES-256-GCM encryption backed by Android Keystore. Authenticate with biometrics or PIN to access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onRequestUnlock,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock with Biometrics")
                    }
                }
            }
        } else {
            // Unlocked State List
            if (uiState.vaultClips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Vault is empty. Click the lock icon on any clip in history to secure it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.vaultClips, key = { it.id }) { clip ->
                        ClipItemCard(
                            clip = clip,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(clip.content))
                            },
                            onTogglePin = {
                                onIntent(ClipboardIntent.TogglePin(clip.id, clip.isPinned))
                            },
                            onToggleVault = {
                                onIntent(ClipboardIntent.ToggleVault(clip.id, clip.isVaulted, clip.content))
                            },
                            onDelete = {
                                onIntent(ClipboardIntent.DeleteClip(clip.id))
                            }
                        )
                    }
                }
            }
        }
    }
}
