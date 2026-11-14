package com.clipvault.android.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ClipVaultFilterBar(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    totalCount: Int = 12,
    availableCustomTags: List<String> = listOf("Office", "Personal", "Study"),
    onOpenFilterSheet: () -> Unit = {},
    onAddTagClick: () -> Unit = {}
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val systemFilterColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = Color.White,
        selectedLeadingIconColor = Color.White,
        containerColor = MaterialTheme.colorScheme.surface,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Row 1: System Filters & Filter Action Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val allChipLabel = "All ($totalCount)"
            val systemFilters = listOf(allChipLabel, "Pinned", "Text", "Url", "Code", "Email", "Phone")

            systemFilters.forEach { filter ->
                val isSelected = selectedFilter.equals(filter, ignoreCase = true) ||
                    (filter.startsWith("All") && selectedFilter.startsWith("All"))
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterSelected(filter) },
                    colors = systemFilterColors,
                    label = { Text(filter) }
                )
            }

            // Filter Drawer / Date Filter Bottom Sheet Icon Button
            IconButton(onClick = onOpenFilterSheet) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filters",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Row 2: Custom Tags (Without Hash # symbol)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val defaultTagIcons = mapOf(
                "office" to Icons.Default.Work,
                "personal" to Icons.Default.Person,
                "study" to Icons.Default.Bookmark
            )
            val tagsToDisplay = if (availableCustomTags.isNotEmpty()) {
                availableCustomTags
            } else {
                listOf("Office", "Personal", "Study")
            }

            tagsToDisplay.forEach { rawTagName ->
                val tagName = rawTagName.removePrefix("#")
                val tagIcon = defaultTagIcons[tagName.lowercase()] ?: Icons.Outlined.Label
                val isSelected = selectedTag != null && selectedTag.removePrefix("#").equals(tagName, ignoreCase = true)

                AssistChip(
                    onClick = { onTagSelected(if (isSelected) null else tagName) },
                    label = { Text(tagName) },
                    leadingIcon = {
                        Icon(
                            imageVector = tagIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        labelColor = if (isSelected) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else if (isDark) {
                            Color(0xFF222F49)
                        } else {
                            Color(0xFFE2E8F0)
                        }
                    )
                )
            }

            // "+ Tag" Chip to create a new custom tag
            AssistChip(
                onClick = onAddTagClick,
                label = { Text("Tag") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Tag",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    }
}
