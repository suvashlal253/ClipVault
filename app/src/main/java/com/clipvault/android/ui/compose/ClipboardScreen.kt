package com.clipvault.android.ui.compose

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clipvault.android.R
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
        val visibleItemsInfo = layoutInfo.visibleItemsInfo
        val visibleItems = visibleItemsInfo.size
        if (totalItems > visibleItems && totalItems > 0) {
            val barWidthPx = width.toPx()
            val trackTopPad = 8.dp.toPx()
            val trackHeight = (size.height - (trackTopPad * 2f)).coerceAtLeast(36.dp.toPx())
            val thumbHeight = (trackHeight * (visibleItems.toFloat() / totalItems.toFloat()))
                .coerceIn(36.dp.toPx(), trackHeight * 0.55f)
            val firstItemSize = (visibleItemsInfo.firstOrNull()?.size ?: 1).coerceAtLeast(1)
            val fractionalItemOffset = (state.firstVisibleItemScrollOffset.toFloat() / firstItemSize.toFloat())
                .coerceIn(0f, 1f)
            val maxScrollIndex = (totalItems - visibleItems).coerceAtLeast(1)
            val scrollFraction = ((state.firstVisibleItemIndex.toFloat() + fractionalItemOffset) / maxScrollIndex.toFloat())
                .coerceIn(0f, 1f)
            val thumbOffsetY = trackTopPad + scrollFraction * (trackHeight - thumbHeight)
            val xOffset = size.width - barWidthPx - 3.dp.toPx()

            // Draw subtle scrollbar track
            drawRoundRect(
                color = trackColor.copy(alpha = alpha * 0.5f),
                topLeft = Offset(xOffset, trackTopPad),
                size = Size(barWidthPx, trackHeight),
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
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }

    // 1. Local search state to prevent cursor jumps and typing reversal
    var localSearchQuery by remember { mutableStateOf(uiState.searchQuery) }
    LaunchedEffect(uiState.searchQuery) {
        if (localSearchQuery != uiState.searchQuery) {
            localSearchQuery = uiState.searchQuery
        }
    }

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    // Premium Gradient Brushes for TopAppBar Brand Header (Crisp high-contrast in Light Mode, glowing neon in Dark Mode)
    val brandTextGradient = remember(isDark) {
        if (isDark) {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF38BDF8), // Glowing Cyan
                    Color(0xFF818CF8), // Electric Indigo
                    Color(0xFFC084FC), // Vibrant Purple/Violet
                    Color(0xFF22D3EE)  // Luminous Aqua
                )
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF4F46E5), // Deep Brand Indigo
                    Color(0xFF7C3AED), // Vibrant Royal Violet
                    Color(0xFF0284C7)  // Crisp Ocean Cyan
                )
            )
        }
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Prominent Rounded App Icon Badge displaying Official ClipVault Icon with Glowing Gradient Border
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
                                Image(
                                    painter = painterResource(id = R.drawable.clipvault_app_icon),
                                    contentDescription = "ClipVault Brand Icon",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(11.dp))
                                )
                            }

                            // Styled 'ClipVault' Title Container (Sleek Dark Slate in Dark Mode, Crisp Indigo-White Pill in Light Mode)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) {
                                    Color(0xFF1E293B).copy(alpha = 0.55f)
                                } else {
                                    Color(0xFFEEF2FF)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isDark) {
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF38BDF8).copy(alpha = 0.35f),
                                                Color(0xFFA855F7).copy(alpha = 0.35f)
                                            )
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF6366F1).copy(alpha = 0.35f),
                                                Color(0xFF8B5CF6).copy(alpha = 0.35f)
                                            )
                                        )
                                    }
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
                            color = when {
                                uiState.isVaultUnlocked -> Color(0xFF10B981).copy(alpha = 0.15f)
                                isDark -> Color(0xFF1E293B).copy(alpha = 0.7f)
                                else -> Color(0xFFEEF2FF)
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
                                    tint = when {
                                        uiState.isVaultUnlocked -> Color(0xFF059669)
                                        isDark -> Color(0xFF818CF8)
                                        else -> Color(0xFF4F46E5)
                                    },
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
                                    // Show warning confirmation dialog instead of deleting immediately
                                    showClearAllConfirmDialog = true
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
                    // 3. Smooth Scrolling LazyColumn with Visible Modern Vertical Scrollbar (No Swipe-to-Delete to prevent accidental deletions)
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
                            Box(modifier = Modifier.animateItemPlacement()) {
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
                                        // Explicit delete only via trash icon button on card
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

    // 4. Enlarged & Unified Material 3 Edit Clip Dialog (Shared by Home & Secure Vault)
    if (editingClip != null) {
        val targetClip = editingClip!!
        EditClipDialog(
            clip = targetClip,
            isVaultClip = targetClip.isVaulted,
            onDismiss = { editingClip = null },
            onSave = { updatedContent ->
                onIntent(ClipboardIntent.EditClip(targetClip.id, updatedContent))
                editingClip = null
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = "Clip updated",
                        duration = SnackbarDuration.Short
                    )
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

    // Clear All History Warning Confirmation Dialog (Theme-aware for Dark & Crisp Light Mode)
    if (showClearAllConfirmDialog) {
        val dialogBg = if (isDark) Color(0xFF0F1626) else Color(0xFFF8F9FA)
        val titleColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1A1C1E)
        val bodyColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF44474F)
        val cancelBorder = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
        val cancelText = if (isDark) Color(0xFFCBD5E1) else Color(0xFF1A1C1E)

        AlertDialog(
            onDismissRequest = { showClearAllConfirmDialog = false },
            containerColor = dialogBg,
            titleContentColor = titleColor,
            textContentColor = bodyColor,
            icon = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Warning",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Clear All History?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all history? This action cannot be undone. (Encrypted Vault clips remain safe.)",
                    fontSize = 13.sp,
                    lineHeight = 18.5.sp,
                    color = bodyColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // 1. Purge system & local clipboard cache so the last copied item cannot linger or re-sync
                        try {
                            clipboardManager.setText(AnnotatedString(""))
                            val sysClipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                sysClipboard?.clearPrimaryClip()
                            } else {
                                sysClipboard?.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                            }
                        } catch (_: Exception) {}

                        // 2. Reset local search query & dispatch ClearAllHistory intent
                        localSearchQuery = ""
                        onIntent(ClipboardIntent.ClearAllHistory)
                        showClearAllConfirmDialog = false

                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message = "All clipboard history cleared",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearAllConfirmDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, cancelBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isDark) Color.Transparent else Color.White,
                        contentColor = cancelText
                    )
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

/**
 * Enlarged & Unified Material 3 Edit Clip Dialog used across both Home and Secure Vault screens.
 * Supports both Signature Dark Mode (#0F1626 & #161F33) and Overhauled Crisp Light Mode (#F8F9FA & #FFFFFF).
 */
@Composable
fun EditClipDialog(
    clip: ClipEntity,
    isVaultClip: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    // Theme-aware tokens (Dark Mode untouched, Light Mode crisp #F8F9FA & pure #FFFFFF)
    val dialogBg = if (isDark) Color(0xFF0F1626) else Color(0xFFF8F9FA)
    val dialogBorder = when {
        isVaultClip -> Color(0xFF10B981).copy(alpha = 0.45f)
        isDark -> Color(0xFF1E293B)
        else -> Color(0xFFE2E8F0)
    }
    val cardSurface = if (isDark) Color(0xFF161F33) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF222F49) else Color(0xFFE2E8F0)
    val textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1A1C1E)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF44474F)
    val closeBtnBg = if (isDark) Color(0xFF1E293B) else Color(0xFFEFF2F6)
    val closeBtnTint = if (isDark) Color(0xFFCBD5E1) else Color(0xFF1A1C1E)
    val dividerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE4E7EC)
    val primaryAccent = if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5)

    var editedContent by remember(clip.id) { mutableStateOf(clip.content) }
    val charCount = editedContent.length
    val lineCount = remember(editedContent) {
        if (editedContent.isEmpty()) 1 else editedContent.lines().size
    }
    val isCode = clip.category == ClipCategory.CODE

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(min = 430.dp, max = 660.dp),
            shape = RoundedCornerShape(26.dp),
            color = dialogBg,
            tonalElevation = if (isDark) 12.dp else 4.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, dialogBorder)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Clean Dialog Header matching SettingsDialog
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isVaultClip) Color(0xFF10B981).copy(alpha = 0.16f)
                                    else primaryAccent.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isVaultClip) Icons.Default.Lock else Icons.Default.Edit,
                                contentDescription = null,
                                tint = if (isVaultClip) Color(0xFF059669) else primaryAccent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isVaultClip) "Edit Encrypted Vault Clip" else "Edit Clip",
                                fontSize = 18.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimary,
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = if (isVaultClip) {
                                    "AES-256-GCM Protected • Re-encrypts automatically on save"
                                } else {
                                    "Expanded workspace for long text & code • Position locked"
                                },
                                fontSize = 11.5.sp,
                                color = if (isVaultClip) Color(0xFF059669) else textSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(closeBtnBg)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = closeBtnTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = dividerColor)

                // 2. Metadata Bar & Quick Editor Tools
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = cardSurface,
                        shadowElevation = if (isDark) 0.dp else 1.dp,
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = primaryAccent.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = clip.category.name,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFFA5B4FC) else primaryAccent,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "$charCount chars • $lineCount lines",
                                    fontSize = 11.5.sp,
                                    color = textSecondary
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(editedContent)) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        "Copy",
                                        fontSize = 11.5.sp,
                                        color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                TextButton(
                                    onClick = { editedContent = "" },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        "Clear",
                                        fontSize = 11.5.sp,
                                        color = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // 3. Large Multi-Line Text Editor Box
                    OutlinedTextField(
                        value = editedContent,
                        onValueChange = { editedContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 250.dp, max = 390.dp),
                        placeholder = {
                            Text(
                                text = "Enter or modify your clip text or code snippet...",
                                color = if (isDark) Color(0xFF64748B) else Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                        },
                        minLines = 10,
                        maxLines = 18,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = if (isCode) 13.sp else 14.5.sp,
                            lineHeight = if (isCode) 19.sp else 21.sp,
                            fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Default,
                            color = textPrimary,
                            textDirection = TextDirection.Ltr,
                            textAlign = TextAlign.Start
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = cardSurface,
                            unfocusedContainerColor = cardSurface,
                            focusedBorderColor = primaryAccent,
                            unfocusedBorderColor = cardBorder,
                            cursorColor = primaryAccent
                        )
                    )
                }

                HorizontalDivider(color = dividerColor)

                // 4. Prominent Footer Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isDark) Color.Transparent else Color.White,
                            contentColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF1A1C1E)
                        ),
                        contentPadding = PaddingValues(vertical = 11.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            if (editedContent.isNotBlank()) {
                                onSave(editedContent.trim())
                            }
                        },
                        enabled = editedContent.isNotBlank(),
                        modifier = Modifier.weight(1.45f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryAccent,
                            contentColor = Color.White,
                            disabledContainerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                            disabledContentColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                        ),
                        contentPadding = PaddingValues(vertical = 11.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save Changes",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
