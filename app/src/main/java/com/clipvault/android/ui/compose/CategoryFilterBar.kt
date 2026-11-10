package com.clipvault.android.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clipvault.android.data.local.entity.ClipCategory

@Composable
fun CategoryFilterBar(
    selectedCategory: ClipCategory?,
    showPinnedOnly: Boolean,
    totalCount: Int,
    onSelectCategory: (ClipCategory?) -> Unit,
    onTogglePinnedOnly: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedCategory == null && !showPinnedOnly,
                onClick = { onSelectCategory(null) },
                label = { Text("All ($totalCount)") }
            )
        }

        item {
            FilterChip(
                selected = showPinnedOnly,
                onClick = onTogglePinnedOnly,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Pinned") }
            )
        }

        items(ClipCategory.values()) { category ->
            FilterChip(
                selected = selectedCategory == category && !showPinnedOnly,
                onClick = {
                    val target = if (selectedCategory == category) null else category
                    onSelectCategory(target)
                },
                label = {
                    Text(category.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            )
        }
    }
}
