package com.clipvault.android.ui.compose

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.ui.viewmodel.ClipboardIntent
import com.clipvault.android.ui.viewmodel.ClipboardUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardScreen(
    uiState: ClipboardUiState,
    onIntent: (ClipboardIntent) -> Unit,
    onOpenVault: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAboutDeveloper: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    var isMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "ClipVault", 
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                actions = {
                    // Essential Action 1: Vault Lock Toggle
                    IconButton(onClick = onOpenVault) {
                        Icon(
                            imageVector = if (uiState.isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Secure Vault"
                        )
                    }

                    // Essential Action 2: Clean Overflow Dropdown Menu
                    IconButton(onClick = { isMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }

                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("About Developer") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            onClick = {
                                isMenuExpanded = false
                                onOpenAboutDeveloper()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Privacy Policy") },
                            leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                            onClick = {
                                isMenuExpanded = false
                                onOpenPrivacyPolicy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                isMenuExpanded = false
                                onOpenSettings()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Clear History") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                isMenuExpanded = false
                                onIntent(ClipboardIntent.ClearUnpinned)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Clean Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { onIntent(ClipboardIntent.Search(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search your clips...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onIntent(ClipboardIntent.Search("")) }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // Category Filter Bar
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedCategory == null && !uiState.showPinnedOnly,
                        onClick = { onIntent(ClipboardIntent.SelectCategory(null)) },
                        label = { Text("All (\${uiState.totalCount})") }
                    )
                }

                items(ClipCategory.values()) { category ->
                    FilterChip(
                        selected = uiState.selectedCategory == category,
                        onClick = {
                            val target = if (uiState.selectedCategory == category) null else category
                            onIntent(ClipboardIntent.SelectCategory(target))
                        },
                        label = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            // Clips List
            if (uiState.clips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.searchQuery.isNotEmpty()) "No clips matching '\${uiState.searchQuery}'" else "Clipboard is empty. Copy any text anywhere to auto-capture!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.clips,
                        key = { it.id }
                    ) { clip ->
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
