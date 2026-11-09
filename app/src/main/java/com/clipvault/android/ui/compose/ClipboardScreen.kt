package com.clipvault.android.ui.compose

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.ui.viewmodel.ClipboardIntent
import com.clipvault.android.ui.viewmodel.ClipboardUiState
import java.text.SimpleDateFormat
import java.util.*

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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isMenuExpanded by remember { mutableStateOf(false) }
    var showAddClipDialog by remember { mutableStateOf(false) }
    var newClipText by remember { mutableStateOf("") }
    var editingClip by remember { mutableStateOf<ClipEntity?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 1. Local search state to prevent cursor jumps and typing reversal
    var localSearchQuery by remember { mutableStateOf(uiState.searchQuery) }
    LaunchedEffect(uiState.searchQuery) {
        if (localSearchQuery != uiState.searchQuery) {
            localSearchQuery = uiState.searchQuery
        }
    }

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
                            text = { Text("Clear All History") },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                isMenuExpanded = false
                                onIntent(ClipboardIntent.ClearAllHistory)
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
            // 1. Search Bar with standard LTR alignment and local input state
            OutlinedTextField(
                value = localSearchQuery,
                onValueChange = {
                    localSearchQuery = it
                    onIntent(ClipboardIntent.Search(it))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search your clips...") },
                textStyle = LocalTextStyle.current.copy(
                    textDirection = TextDirection.Ltr,
                    textAlign = TextAlign.Start
                ),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (localSearchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            localSearchQuery = ""
                            onIntent(ClipboardIntent.Search(""))
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // Category & Date Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedCategory == null && !uiState.showPinnedOnly && uiState.selectedDateMillis == null,
                        onClick = {
                            onIntent(ClipboardIntent.SelectCategory(null))
                            if (uiState.selectedDateMillis != null) {
                                onIntent(ClipboardIntent.FilterByDate(null))
                            }
                        },
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

                // 4. Date Filter Chip
                item {
                    val dateLabel = if (uiState.selectedDateMillis != null) {
                        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(uiState.selectedDateMillis))
                    } else {
                        "Date"
                    }
                    FilterChip(
                        selected = uiState.selectedDateMillis != null,
                        onClick = { showDatePicker = true },
                        label = { Text(dateLabel) },
                        leadingIcon = {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        trailingIcon = {
                            if (uiState.selectedDateMillis != null) {
                                IconButton(
                                    onClick = { onIntent(ClipboardIntent.FilterByDate(null)) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear Date", modifier = Modifier.size(12.dp))
                                }
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
                            text = when {
                                uiState.searchQuery.isNotEmpty() -> "No clips matching '" + uiState.searchQuery + "'"
                                uiState.selectedDateMillis != null -> "No clips recorded on this date."
                                else -> "Clipboard is empty. Copy text anywhere or tap + to add!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
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
                            onEdit = {
                                editingClip = clip
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

    // Native DatePickerDialog for past date filtering
    if (showDatePicker) {
        val calendar = Calendar.getInstance()
        if (uiState.selectedDateMillis != null) {
            calendar.timeInMillis = uiState.selectedDateMillis
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                val chosenCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, selectedDay)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onIntent(ClipboardIntent.FilterByDate(chosenCal.timeInMillis))
                showDatePicker = false
            },
            year,
            month,
            day
        ).apply {
            setOnDismissListener { showDatePicker = false }
            show()
        }
    }

    // 4. Quick Edit Dialog
    if (editingClip != null) {
        val targetClip = editingClip!!
        var editedContent by remember(targetClip.id) { mutableStateOf(targetClip.content) }

        AlertDialog(
            onDismissRequest = { editingClip = null },
            title = { Text("Edit Clip") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Modify clip content:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editedContent,
                        onValueChange = { editedContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                        textStyle = LocalTextStyle.current.copy(
                            textDirection = TextDirection.Ltr,
                            textAlign = TextAlign.Start
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editedContent.isNotBlank()) {
                            onIntent(ClipboardIntent.EditClip(targetClip.id, editedContent))
                            editingClip = null
                        }
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingClip = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add New Clip Dialog
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
                    minLines = 3,
                    textStyle = LocalTextStyle.current.copy(
                        textDirection = TextDirection.Ltr,
                        textAlign = TextAlign.Start
                    )
                )
            },
            confirmButton = {
                Button(
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
