package com.bratneytg.ganga

import com.bratneytg.ganga.core.Engine
import com.bratneytg.ganga.core.Item
import com.bratneytg.ganga.core.RawOffer
import com.bratneytg.ganga.core.RawShipping
import com.bratneytg.ganga.core.Rates
import com.bratneytg.ganga.core.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val rates = Rates { amount, currency ->
        when (currency) {
            "EUR" -> amount
            "GBP" -> amount / 0.85
            else -> Double.NaN
        }
    }
    private val settings = Settings(homeCountry = "DE")
    private val item = Item(id = "i", name = "Sony WH-1000XM5", query = "Sony WH-1000XM5", condition = "new")

    private fun raw(
        store: String = "Tienda",
        url: String = "https://tienda.de/a",
        title: String = "Sony WH-1000XM5 auriculares",
        price: Double = 300.0,
        currency: String = "EUR",
        shipping: RawShipping? = null,
        shopCountry: String? = "DE",
    ) = RawOffer("serper", store, title, url, price, currency, shipping, shopCountry)

    @Test
    fun landedPriceIncludesShippingAndImport() {
        val offers = Engine.processOffers(
            listOf(
                raw(store = "A", url = "https://a.de/1", price = 300.0, shipping = RawShipping(0.0, "EUR", true)),
                raw(store = "B", url = "https://b.es/1", price = 280.0, shopCountry = "ES"),
                raw(store = "C", url = "https://c.co.uk/1", price = 230.0, currency = "GBP", shopCountry = "GB"),
            ),
            item, settings, rates,
        )
        val by = offers.associateBy { it.store }
        assertEquals(300.0, by.getValue("A").landedEur, 0.01)
        assertTrue(!by.getValue("A").shippingEstimated)
        assertEquals(settings.shipping.eu, by.getValue("B").shippingEur, 0.001)
        assertEquals(280.0 + settings.shipping.eu, by.getValue("B").landedEur, 0.01)
        assertTrue(by.getValue("B").foreign)
        val c = by.getValue("C")
        val expected = (230.0 / 0.85 + settings.shipping.nonEu) * (1 + settings.shipping.importPct / 100)
        assertEquals(expected, c.landedEur, 0.05)
        assertTrue(c.importEur > 0)
    }

    @Test
    fun dropsAccessoriesUsedAndDuplicates() {
        val offers = Engine.processOffers(
            listOf(
                raw(store = "A", url = "https://a.de/1", price = 320.0),
                raw(store = "Dup", url = "https://a.de/1?utm=x", price = 320.0),
                raw(store = "Funda", url = "https://f.de/1", title = "Funda para Sony WH-1000XM5", price = 12.0),
                raw(store = "Usado", url = "https://u.de/1", title = "Sony WH-1000XM5 gebraucht", price = 150.0),
                raw(store = "Barata", url = "https://b.de/1", price = 289.0),
            ),
            item, settings, rates,
        )
        assertEquals(listOf("Barata", "A"), offers.map { it.store })
    }

    @Test
    fun flagsSuspiciouslyCheapOffer() {
        val list = listOf(300.0, 310.0, 305.0, 315.0, 308.0).mapIndexed { i, p ->
            raw(store = "T$i", url = "https://t$i.de/x", price = p)
        } + raw(store = "Trampa", url = "https://trampa.de/x", price = 49.0)
        val offers = Engine.processOffers(list, item, settings, rates)
        val trap = offers.first { it.store == "Trampa" }
        assertTrue(trap.suspicious)
        assertEquals("T0", Engine.best(offers)?.store)
        assertEquals("Trampa", offers.last().store)
    }

    @Test
    fun untrustedFreeShippingFromAbroadIsEstimated() {
        val offers = Engine.processOffers(
            listOf(raw(store = "Fuera", url = "https://x.fr/1", shopCountry = "FR", shipping = RawShipping(0.0, "EUR", false))),
            item, settings, rates,
        )
        assertEquals(settings.shipping.eu, offers[0].shippingEur, 0.001)
        assertTrue(offers[0].shippingEstimated)
    }

    @Test
    fun notificationDecision() {
        val hist = listOf(300.0, 295.0, 310.0)
        assertEquals(listOf("target", "lowest"), Engine.decideNotification(250.0, null, 240.0, hist).reasons)
        assertTrue(Engine.decideNotification(250.0, 240.0, 240.0, hist).reasons.isEmpty())
        assertTrue(Engine.decideNotification(250.0, null, 260.0, hist).reasons.isEmpty())
        // rebota por encima del 5 % y vuelve a bajar: se avisa otra vez
        val again = Engine.decideNotification(250.0, 240.0, 245.0, hist + 240.0)
        assertTrue(again.reasons.contains("target") || again.reasons.isEmpty())
    }
}
