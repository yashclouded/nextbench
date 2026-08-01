package com.nextbench.app.marketplace

import com.nextbench.data.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceViewModelTest {
    @Test
    fun `search matches title seller and location while category remains exact`() {
        val products = listOf(
            Product(id = "book", title = "Organic Chemistry", category = "Books", sellerName = "Maya", city = "Lucknow"),
            Product(id = "lamp", title = "Desk lamp", category = "Hostel", sellerName = "Dev", city = "Delhi"),
        )

        assertEquals(listOf("book"), filterAndSortProducts(products, "maya", AllCategory, MarketplaceSort.Newest).map(Product::id))
        assertEquals(listOf("lamp"), filterAndSortProducts(products, "", "Hostel", MarketplaceSort.Newest).map(Product::id))
    }

    @Test
    fun `price sorting is deterministic and newest breaks ties`() {
        val products = listOf(
            Product(id = "old", price = 100, createdAt = null),
            Product(id = "same", price = 100),
            Product(id = "high", price = 500),
        )

        assertEquals(listOf("old", "same", "high"), filterAndSortProducts(products, "", AllCategory, MarketplaceSort.PriceLow).map(Product::id))
        assertEquals(listOf("high", "old", "same"), filterAndSortProducts(products, "", AllCategory, MarketplaceSort.PriceHigh).map(Product::id))
    }

    @Test
    fun `merge keeps the first server copy for stable pagination`() {
        val merged = mergeProducts(
            listOf(Product(id = "one"), Product(id = "two", title = "old")),
            listOf(Product(id = "two", title = "new"), Product(id = "three")),
        )

        assertEquals(listOf("one", "two", "three"), merged.map(Product::id))
        assertEquals("old", merged[1].title)
    }

    @Test
    fun `viewer authentication requires a nonblank id`() {
        assertFalse(MarketplaceViewer().signedIn)
        assertFalse(MarketplaceViewer(uid = " ").signedIn)
        assertTrue(MarketplaceViewer(uid = "student-1").signedIn)
    }
}
