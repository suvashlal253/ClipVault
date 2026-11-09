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
    isAccessibilityActive: Boolean = true,
    onIntent: (ClipboardIntent) -> Unit,
    onRequestEnableAccessibility: () -> Unit = {},
    onOpenVault: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAboutDeveloper: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    var isMenuExpanded by remember { mutableStateOf(false) }
    var showAddClipDialog by remember { mutableStateOf(false) }
    var newClipText by remember { mutableStateOf("") }

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
                    IconButton(onClick = onOpenVault) {
                        Icon(
                            imageVector = if (uiState.isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Secure Vault"
                        )
                    }

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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val clip = clipboardManager.getText()?.text
                    if (!clip.isNullOrBlank()) {
                        onIntent(ClipboardIntent.SaveNewClip(clip, "Pasted via FAB"))
                    } else {
                        showAddClipDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Clip")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { onIntent(ClipboardIntent.Search(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
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
                        label = { Text("All (" + uiState.totalCount + ")") }
                    )
                }

                item {
                    FilterChip(
                        selected = uiState.showPinnedOnly,
                        onClick = { onIntent(ClipboardIntent.TogglePinnedOnly) },
                        label = { Text("Pinned") },
                        leadingIcon = {
                            if (uiState.showPinnedOnly) {
                                Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
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

            if (uiState.clips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (uiState.searchQuery.isNotEmpty()) {
                                ("No clips matching '" + uiState.searchQuery + "'")
                            } else {
                                "Clipboard is empty. Copy text anywhere or tap + to add!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showAddClipDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Test Clip")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
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

    if (showAddClipDialog) {
        AlertDialog(
            onDismissRequest = { showAddClipDialog = false },
            title = { Text("Add New Clip") },
            text = {
                OutlinedTextField(
                    value = newClipText,
                    onValueChange = { newClipText = it },
                    placeholder = { Text("Type or paste text...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newClipText.isNotBlank()) {
                            onIntent(ClipboardIntent.SaveNewClip(newClipText, "Manual Entry"))
                            newClipText = ""
                            showAddClipDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddClipDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
