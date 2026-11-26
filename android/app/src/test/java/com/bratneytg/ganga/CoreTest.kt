package com.bratneytg.ganga

import com.bratneytg.ganga.core.Countries
import com.bratneytg.ganga.core.Delivery
import com.bratneytg.ganga.core.Item
import com.bratneytg.ganga.core.Match
import com.bratneytg.ganga.core.Price
import com.bratneytg.ganga.core.Shipping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    private fun item(query: String, exclude: List<String> = emptyList(), must: List<String> = emptyList()) =
        Item(id = "t", name = query, query = query, exclude = exclude, mustInclude = must)

    @Test
    fun priceParsesEuropeanAndAnglo() {
        val cases = listOf(
            Triple("1.299,00 €", 1299.0, "EUR"),
            Triple("€1,299.00", 1299.0, "EUR"),
            Triple("19,99 €", 19.99, "EUR"),
            Triple("19.99", 19.99, null),
            Triple("1 299,00 €", 1299.0, "EUR"),
            Triple("1.299 €", 1299.0, "EUR"),
            Triple("£249.00", 249.0, "GBP"),
            Triple("99,00 zł", 99.0, "PLN"),
            Triple("1299.99", 1299.99, null),
            Triple("ab 79,90 EUR", 79.9, "EUR"),
            Triple("$1,099", 1099.0, "USD"),
        )
        for ((input, value, currency) in cases) {
            val p = Price.parse(input)
            assertNotNull("no se pudo leer $input", p)
            assertEquals(input, value, p!!.value, 0.001)
            assertEquals(input, currency, p.currency)
        }
        assertNull(Price.parse("sin precio"))
        assertNull(Price.parse(null))
    }

    @Test
    fun relevanceAcceptsProductAndRejectsAccessories() {
        val it = item("Sony WH-1000XM5")
        assertTrue(Match.isRelevant("Sony WH-1000XM5 Auriculares inalámbricos negro", it))
        assertTrue(Match.isRelevant("SONY WH1000XM5 Kopfhörer", it))
        assertTrue(!Match.isRelevant("Sony WH-1000XM4 Auriculares", it))
        assertTrue(!Match.isRelevant("Funda para Sony WH-1000XM5", it))
        assertTrue(!Match.isRelevant("Sony Cámara Alpha 7", it))
    }

    @Test
    fun relevanceHandlesNumbersAndWords() {
        val it = item("iPhone 15 128GB", exclude = listOf("pro"), must = listOf("negro"))
        assertTrue(Match.isRelevant("Apple iPhone 15 128 GB negro", it))
        assertTrue(!Match.isRelevant("Apple iPhone 15 Pro 128 GB negro", it))
        assertTrue(!Match.isRelevant("Apple iPhone 15 128 GB azul", it))
        assertTrue(!Match.isRelevant("Apple iPhone 11 5G 128 GB negro", it))
    }

    @Test
    fun conditionDetection() {
        for (t in listOf("iPhone gebraucht", "iPhone reacondicionado", "iPhone refurbished", "iPhone d'occasion", "iPhone usato")) {
            assertEquals(t, "used", Match.detectCondition(t))
        }
        assertEquals("new", Match.detectCondition("iPhone 15 nuevo"))
    }

    @Test
    fun deliveryParsing() {
        assertEquals(Delivery(0.0, null), Shipping.parseDelivery("Envío gratis"))
        assertEquals(Delivery(0.0, null), Shipping.parseDelivery("Kostenloser Versand"))
        assertEquals(Delivery(4.99, "EUR"), Shipping.parseDelivery("+ 4,99 € de envío"))
        assertNull(Shipping.parseDelivery("Llega el miércoles"))
        assertNull(Shipping.parseDelivery(null))
    }

    @Test
    fun countryFromUrl() {
        assertEquals("DE", Countries.fromUrl("https://www.mediamarkt.de/x"))
        assertNull(Countries.fromUrl("https://www.pccomponentes.com/x"))
        assertEquals("GB", Countries.fromUrl("https://shop.example.co.uk/x"))
        assertNull(Countries.fromUrl("https://www.google.de/search?q=1"))
    }
}
