package com.clipvault.android.ui.compose

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.ui.viewmodel.ClipboardIntent
import com.clipvault.android.ui.viewmodel.ClipboardUiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modern Vertical Scrollbar Modifier for LazyColumn with smooth alpha transition
 */
@Composable
fun Modifier.drawVerticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    thumbColor: Color = Color(0xFF6366F1),
    trackColor: Color = Color(0xFF1E293B).copy(alpha = 0.55f)
): Modifier {
    val targetAlpha = if (state.isScrollInProgress) 1f else 0.45f
    val duration = if (state.isScrollInProgress) 150 else 600
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration),
        label = "ScrollbarAlpha"
    )

    return drawWithContent {
        drawContent()
        val layoutInfo = state.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo.size
        if (totalItems > visibleItems && totalItems > 0) {
            val barWidthPx = width.toPx()
            val viewportHeight = size.height
            val thumbHeight = (viewportHeight * (visibleItems.toFloat() / totalItems.toFloat()))
                .coerceAtLeast(36.dp.toPx())
            val maxScrollOffset = (totalItems - visibleItems).coerceAtLeast(1)
            val scrollFraction = (state.firstVisibleItemIndex.toFloat() / maxScrollOffset.toFloat())
                .coerceIn(0f, 1f)
            val thumbOffsetY = scrollFraction * (viewportHeight - thumbHeight)
            val xOffset = size.width - barWidthPx - 3.dp.toPx()

            // Draw subtle scrollbar track
            drawRoundRect(
                color = trackColor.copy(alpha = alpha * 0.5f),
                topLeft = Offset(xOffset, 8.dp.toPx()),
                size = Size(barWidthPx, viewportHeight - 16.dp.toPx()),
                cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
            )

            // Draw vibrant glowing scrollbar thumb
            drawRoundRect(
                color = thumbColor.copy(alpha = alpha),
                topLeft = Offset(xOffset, thumbOffsetY),
                size = Size(barWidthPx, thumbHeight),
                cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lazyListState = rememberLazyListState()

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

    // Premium Gradient Brushes for TopAppBar Brand Header
    val brandTextGradient = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF38BDF8), // Glowing Cyan
                Color(0xFF818CF8), // Electric Indigo
                Color(0xFFC084FC), // Vibrant Purple/Violet
                Color(0xFF22D3EE)  // Luminous Aqua
            )
        )
    }
    val badgeBgGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF4F46E5), // Deep Electric Indigo
                Color(0xFF7C3AED), // Rich Violet
                Color(0xFF0891B2)  // Cyan Accent
            )
        )
    }
    val badgeBorderGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF38BDF8), // Glowing Cyan Border
                Color(0xFFA855F7), // Glowing Purple Border
                Color(0xFF6366F1)
            )
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Prominent Rounded App Icon Badge with Glowing Gradient Border
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(brush = badgeBgGradient)
                                    .border(
                                        BorderStroke(1.5.dp, badgeBorderGradient),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "ClipVault Brand Icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Styled 'ClipVault' Title with Sleek Glowing Container & ExtraBold Gradient Text
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.55f),
                                border = BorderStroke(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF38BDF8).copy(alpha = 0.35f),
                                            Color(0xFFA855F7).copy(alpha = 0.35f)
                                        )
                                    )
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "ClipVault",
                                        style = TextStyle(
                                            brush = brandTextGradient,
                                            fontSize = 21.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = (-0.4).sp
                                        )
                                    )
                                    // Live Sync Dot Indicator
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        // Prominent Secure Vault Action Button
                        Surface(
                            onClick = onOpenVault,
                            shape = RoundedCornerShape(10.dp),
                            color = if (uiState.isVaultUnlocked) {
                                Color(0xFF10B981).copy(alpha = 0.15f)
                            } else {
                                Color(0xFF1E293B).copy(alpha = 0.7f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (uiState.isVaultUnlocked) {
                                    Color(0xFF10B981).copy(alpha = 0.5f)
                                } else {
                                    Color(0xFF6366F1).copy(alpha = 0.35f)
                                }
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (uiState.isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = "Secure Vault",
                                    tint = if (uiState.isVaultUnlocked) Color(0xFF10B981) else Color(0xFF818CF8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(onClick = { isMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
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
                // Subtle glowing gradient accent line under the TopAppBar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF38BDF8).copy(alpha = 0.45f),
                                    Color(0xFFA855F7).copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF131B2E),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.55f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = data.visuals.message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val clip = clipboardManager.getText()?.text
                    if (!clip.isNullOrBlank()) {
                        onIntent(ClipboardIntent.SaveNewClip(clip, "Pasted via FAB"))
                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar("Pasted from clipboard", duration = SnackbarDuration.Short)
                        }
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
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // Active Filter Tabs & Accents (Vibrant Blue/Purple Accent against Dark Surface)
            val premiumChipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                selectedLeadingIconColor = androidx.compose.ui.graphics.Color.White,
                selectedTrailingIconColor = androidx.compose.ui.graphics.Color.White,
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    val isAllSelected = uiState.selectedCategory == null && !uiState.showPinnedOnly && uiState.selectedDateMillis == null
                    FilterChip(
                        selected = isAllSelected,
                        onClick = {
                            onIntent(ClipboardIntent.SelectCategory(null))
                            if (uiState.selectedDateMillis != null) {
                                onIntent(ClipboardIntent.FilterByDate(null))
                            }
                        },
                        colors = premiumChipColors,
                        label = { Text("All (" + uiState.totalCount + ")") }
                    )
                }

                item {
                    FilterChip(
                        selected = uiState.showPinnedOnly,
                        onClick = { onIntent(ClipboardIntent.TogglePinnedOnly) },
                        colors = premiumChipColors,
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
                        colors = premiumChipColors,
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
                        colors = premiumChipColors,
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
                        Button(
                            onClick = { showAddClipDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = androidx.compose.ui.graphics.Color.White
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Test Clip")
                        }
                    }
                }
            } else {
                val showFastScrollTop by remember {
                    derivedStateOf { lazyListState.firstVisibleItemIndex > 2 }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 3. Smooth Scrolling LazyColumn with Visible Modern Vertical Scrollbar & SwipeToDismiss
                    LazyColumn(
                        state = lazyListState,
                        flingBehavior = ScrollableDefaults.flingBehavior(),
                        modifier = Modifier
                            .fillMaxSize()
                            .drawVerticalScrollbar(lazyListState),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = uiState.clips,
                            key = { it.id },
                            contentType = { it.category }
                        ) { clip ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    when (dismissValue) {
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            // Swipe Right -> Pin / Unpin with Haptic Feedback
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onIntent(ClipboardIntent.TogglePin(clip.id, clip.isPinned))
                                            coroutineScope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar(
                                                    message = if (clip.isPinned) "Clip unpinned" else "Pinned to top",
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                            false // Spring smoothly back into place
                                        }
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            // Swipe Left -> Delete Clip with Haptic Feedback
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onIntent(ClipboardIntent.DeleteClip(clip.id))
                                            coroutineScope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar(
                                                    message = "Clip deleted",
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                            true
                                        }
                                        SwipeToDismissBoxValue.Settled -> false
                                    }
                                },
                                positionalThreshold = { totalDistance -> totalDistance * 0.32f }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier.animateItemPlacement(),
                                backgroundContent = {
                                    val direction = dismissState.dismissDirection
                                    val bgColor by animateColorAsState(
                                        targetValue = when (direction) {
                                            SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4F46E5) // Vibrant Indigo Pin
                                            SwipeToDismissBoxValue.EndToStart -> Color(0xFFEF4444) // Crimson Delete
                                            else -> Color.Transparent
                                        },
                                        label = "SwipeBackground"
                                    )
                                    val iconScale by animateFloatAsState(
                                        targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.85f else 1.2f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                        label = "SwipeIconScale"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(bgColor)
                                            .padding(horizontal = 22.dp),
                                        contentAlignment = when (direction) {
                                            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                            else -> Alignment.CenterEnd
                                        }
                                    ) {
                                        if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = "Pin",
                                                    tint = Color.White,
                                                    modifier = Modifier.scale(iconScale)
                                                )
                                                Text(
                                                    text = if (clip.isPinned) "Unpin" else "Pin",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "Delete",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color.White,
                                                    modifier = Modifier.scale(iconScale)
                                                )
                                            }
                                        }
                                    }
                                },
                                content = {
                                    ClipItemCard(
                                        clip = clip,
                                        onCopy = {
                                            clipboardManager.setText(AnnotatedString(clip.content))
                                            coroutineScope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar(
                                                    message = "Copied to clipboard",
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        },
                                        onEdit = {
                                            editingClip = clip
                                        },
                                        onTogglePin = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onIntent(ClipboardIntent.TogglePin(clip.id, clip.isPinned))
                                        },
                                        onToggleVault = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onIntent(ClipboardIntent.ToggleVault(clip.id, clip.isVaulted, clip.content))
                                        },
                                        onDelete = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onIntent(ClipboardIntent.DeleteClip(clip.id))
                                            coroutineScope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar(
                                                    message = "Clip deleted",
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }

                    // Fast-Scroller "Top" Pill Indicator when scrolled down
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
                                    lazyListState.animateScrollToItem(0)
                                }
                            },
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF4F46E5),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
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

    // 4. Quick Edit Dialog with Elegant Dark Card & High-Contrast Action Button
    if (editingClip != null) {
        val targetClip = editingClip!!
        var editedContent by remember(targetClip.id) { mutableStateOf(targetClip.content) }

        AlertDialog(
            onDismissRequest = { editingClip = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Edit Clip", color = MaterialTheme.colorScheme.onSurface) },
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
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
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
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Add New Clip", color = MaterialTheme.colorScheme.onSurface) },
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
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
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
