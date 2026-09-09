package io.github.fmaruejol.ardoise.ui.components

import io.github.fmaruejol.ardoise.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The point of a category icon is that the feed can be scanned without reading
 * it, so two categories sharing a glyph defeats the whole thing.
 */
class CategoryIconsTest {
    /** `groups.categories` as the server seeds it: 44 across seven groupings. */
    private val categories = listOf(
        "Uncategorized" to "General",
        "Uncategorized" to "Payment",
        "Entertainment" to "Entertainment",
        "Entertainment" to "Games",
        "Entertainment" to "Movies",
        "Entertainment" to "Music",
        "Entertainment" to "Sports",
        "Food and Drink" to "Food and Drink",
        "Food and Drink" to "Dining Out",
        "Food and Drink" to "Groceries",
        "Food and Drink" to "Liquor",
        "Home" to "Home",
        "Home" to "Electronics",
        "Home" to "Furniture",
        "Home" to "Household Supplies",
        "Home" to "Maintenance",
        "Home" to "Mortgage",
        "Home" to "Pets",
        "Home" to "Rent",
        "Home" to "Services",
        "Life" to "Childcare",
        "Life" to "Clothing",
        "Life" to "Education",
        "Life" to "Gifts",
        "Life" to "Insurance",
        "Life" to "Medical Expenses",
        "Life" to "Taxes",
        "Life" to "Donation",
        "Transportation" to "Transportation",
        "Transportation" to "Bicycle",
        "Transportation" to "Bus/Train",
        "Transportation" to "Car",
        "Transportation" to "Gas/Fuel",
        "Transportation" to "Hotel",
        "Transportation" to "Parking",
        "Transportation" to "Plane",
        "Transportation" to "Taxi",
        "Utilities" to "Utilities",
        "Utilities" to "Cleaning",
        "Utilities" to "Electricity",
        "Utilities" to "Heat/Gas",
        "Utilities" to "Trash",
        "Utilities" to "TV/Phone/Internet",
        "Utilities" to "Water",
    )

    @Test
    fun `covers every category the server sends`() {
        assertEquals(44, categories.size)
    }

    @Test
    fun `gives every category its own icon`() {
        val icons = categories.associateWith { (grouping, name) -> categoryIcon(grouping, name) }

        val shared = icons.entries
            .groupBy { it.value }
            .filterValues { it.size > 1 }
            .map { (_, entries) -> entries.map { "${it.key.first}/${it.key.second}" } }

        assertTrue("these share an icon: $shared", shared.isEmpty())
    }

    @Test
    fun `no known category falls through to the fallback`() {
        // The fallback is what an unrecognised category gets, so a real one
        // landing on it means the mapping has a hole rather than a choice.
        val mapped = categories - ("Uncategorized" to "General")
        for ((grouping, name) in mapped) {
            assertNotEquals(
                "$grouping/$name has no icon of its own",
                R.drawable.ic_payments,
                categoryIcon(grouping, name),
            )
        }
    }

    @Test
    fun `an unknown category falls back rather than crashing`() {
        assertEquals(R.drawable.ic_payments, categoryIcon("Something", "New"))
        assertEquals(R.drawable.ic_payments, categoryIcon(null, null))
    }

    @Test
    fun `matches the web client where the web client is distinctive`() {
        // A Spliit user moving between the two clients should recognise the
        // same expense; these are the ones with an obvious glyph.
        assertEquals(R.drawable.ic_restaurant, categoryIcon("Food and Drink", "Food and Drink"))
        assertEquals(R.drawable.ic_shopping_cart, categoryIcon("Food and Drink", "Groceries"))
        assertEquals(R.drawable.ic_home, categoryIcon("Home", "Home"))
        assertEquals(R.drawable.ic_pets, categoryIcon("Home", "Pets"))
        assertEquals(R.drawable.ic_flight, categoryIcon("Transportation", "Plane"))
        assertEquals(R.drawable.ic_local_taxi, categoryIcon("Transportation", "Taxi"))
    }
}
