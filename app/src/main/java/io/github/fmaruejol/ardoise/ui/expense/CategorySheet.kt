package io.github.fmaruejol.ardoise.ui.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.ui.components.categoryColors
import io.github.fmaruejol.ardoise.ui.components.categoryIcon

/**
 * Choosing a category. The server sends 44 categories across seven
 * groupings, and the grid is the real list rather than the groupings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySheet(
    categories: List<Category>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = stringResource(R.string.expense_category),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.heightIn(max = 480.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryTile(
                    category = category,
                    selected = category.id == selectedId,
                    onClick = { onSelect(category.id) },
                )
            }
        }
    }
}

/**
 * One category, at a fixed height rather than one that follows the label:
 * names run from "Rent" to "Household Supplies".
 */
@Composable
internal fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit) {
    val (container, content) = categoryColors(category.grouping)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(CategoryTileHeight)
            .background(
                if (selected) container else MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(categoryIcon(category.grouping, category.name)),
            contentDescription = null,
            tint = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Every tile is exactly this tall. Internal so `CategoryTileTest` can assert
 * it: measuring one tile against another passes on any width where neither
 * happens to wrap.
 */
internal val CategoryTileHeight = 96.dp
