package io.github.fmaruejol.ardoise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.ui.theme.groupTiles

/**
 * An icon for a category, keyed on `grouping/name` exactly as the web client
 * does, so the same expense is recognisable in both. The glyphs are the
 * Material Symbols nearest its Lucide ones.
 *
 * **Distinct on purpose, including where upstream reuses one**: four icons for
 * four things is what makes a feed scannable. An unknown category falls back.
 */
fun categoryIcon(grouping: String?, name: String?): Int =
    when ("${grouping.orEmpty()}/${name.orEmpty()}") {
        "Uncategorized/General" -> R.drawable.ic_payments
        "Uncategorized/Payment" -> R.drawable.ic_paid
        "Entertainment/Entertainment" -> R.drawable.ic_attractions
        "Entertainment/Games" -> R.drawable.ic_casino
        "Entertainment/Movies" -> R.drawable.ic_movie
        "Entertainment/Music" -> R.drawable.ic_music_note
        "Entertainment/Sports" -> R.drawable.ic_fitness_center
        "Food and Drink/Food and Drink" -> R.drawable.ic_restaurant
        "Food and Drink/Dining Out" -> R.drawable.ic_local_bar
        "Food and Drink/Groceries" -> R.drawable.ic_shopping_cart
        "Food and Drink/Liquor" -> R.drawable.ic_wine_bar
        "Home/Home" -> R.drawable.ic_home
        "Home/Electronics" -> R.drawable.ic_power
        "Home/Furniture" -> R.drawable.ic_chair
        "Home/Household Supplies" -> R.drawable.ic_lightbulb
        "Home/Maintenance" -> R.drawable.ic_build
        "Home/Services" -> R.drawable.ic_handyman
        "Home/Mortgage" -> R.drawable.ic_account_balance
        "Home/Pets" -> R.drawable.ic_pets
        "Home/Rent" -> R.drawable.ic_savings
        "Life/Childcare" -> R.drawable.ic_child_care
        "Life/Clothing" -> R.drawable.ic_checkroom
        "Life/Donation" -> R.drawable.ic_volunteer_activism
        "Life/Education" -> R.drawable.ic_school
        "Life/Gifts" -> R.drawable.ic_redeem
        "Life/Insurance" -> R.drawable.ic_shield
        "Life/Medical Expenses" -> R.drawable.ic_stethoscope
        "Life/Taxes" -> R.drawable.ic_receipt_long
        "Transportation/Transportation" -> R.drawable.ic_directions_bus
        "Transportation/Bicycle" -> R.drawable.ic_directions_bike
        "Transportation/Bus/Train" -> R.drawable.ic_train
        "Transportation/Car" -> R.drawable.ic_directions_car
        "Transportation/Gas/Fuel" -> R.drawable.ic_local_gas_station
        "Transportation/Hotel" -> R.drawable.ic_hotel
        "Transportation/Parking" -> R.drawable.ic_local_parking
        "Transportation/Plane" -> R.drawable.ic_flight
        "Transportation/Taxi" -> R.drawable.ic_local_taxi
        "Utilities/Utilities" -> R.drawable.ic_bolt
        "Utilities/Cleaning" -> R.drawable.ic_cleaning_services
        "Utilities/Electricity" -> R.drawable.ic_electric_bolt
        "Utilities/Heat/Gas" -> R.drawable.ic_local_fire_department
        "Utilities/Trash" -> R.drawable.ic_delete
        "Utilities/TV/Phone/Internet" -> R.drawable.ic_router
        "Utilities/Water" -> R.drawable.ic_water_drop
        else -> R.drawable.ic_payments
    }

/**
 * The round tinted badge an expense wears in the feed: the category's icon,
 * tinted by its **grouping**, so a run of transport expenses reads as a run.
 */
@Composable
fun CategoryBadge(
    category: Category?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    val (container, content) = categoryColors(category?.grouping)
    Box(
        modifier = modifier
            .size(size)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(categoryIcon(category?.grouping, category?.name)),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun categoryColors(grouping: String?): Pair<Color, Color> =
    groupTiles.let { tiles -> tiles[Math.floorMod(grouping.orEmpty().hashCode(), tiles.size)] }
