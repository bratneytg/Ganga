package com.bratneytg.ganga.providers

import com.bratneytg.ganga.core.Item
import com.bratneytg.ganga.core.Price
import com.bratneytg.ganga.core.RawOffer
import com.bratneytg.ganga.core.RawShipping
import com.bratneytg.ganga.core.Settings
import com.bratneytg.ganga.net.Fx
import java.net.URLEncoder
import java.time.LocalDateTime

/**
 * Proveedor de prueba: inventa ofertas verosímiles (los precios cambian en cada tramo de 6 horas)
 * para poder ver cómo funciona la app sin ninguna clave. Se activa en Ajustes > Modo demo.
 */
object DemoProvider : Provider {
    override val id = "demo"
    override val name = "Demo (datos inventados)"

    private class DemoStore(val store: String, val country: String, val ship: Double?)

    private val stores = listOf(
        DemoStore("MegaTienda", "DE", 0.0),
        DemoStore("ElectroMax", "DE", 4.9),
        DemoStore("PCbarato", "ES", 6.5),
        DemoStore("Tecnomás", "ES", 0.0),
        DemoStore("LeBonPrix", "FR", null),
        DemoStore("Negozio24", "IT", null),
        DemoStore("DutchDeals", "NL", 5.95),
        DemoStore("LondonGadgets", "GB", null),
        DemoStore("Outlet Polska", "PL", 8.0),
    )

    override fun isConfigured(settings: Settings) = settings.demoMode

    private fun hash(str: String): Long {
        var h = 2166136261L
        for (ch in str) {
            h = h xor ch.code.toLong()
            h = (h * 16777619L) and 0xFFFFFFFFL
        }
        return h
    }

    override suspend fun search(item: Item, settings: Settings, fx: Fx): ProviderResult {
        val now = LocalDateTime.now()
        val slot = "${now.toLocalDate()}-${now.hour / 6}"
        var seed = hash("${item.name}|$slot")
        val rand = {
            seed = (seed * 1664525L + 1013904223L) and 0xFFFFFFFFL
            seed / 4294967296.0
        }
        val base = item.targetPrice?.let { it * 1.12 } ?: (40.0 + (hash(item.name) % 360))
        val labels = listOf("nuevo", "precintado", "original")

        val offers = stores.mapIndexed { i, s ->
            val priceEur = Price.round2(base * (0.82 + rand() * 0.45))
            val currency = when (s.country) {
                "GB" -> "GBP"
                "PL" -> "PLN"
                else -> "EUR"
            }
            val rate = if (currency == "EUR") 1.0 else (fx.rates[currency] ?: 1.0)
            RawOffer(
                provider = "demo",
                store = s.store,
                title = "${item.name} (${labels[i % labels.size]})",
                url = "https://example.com/demo/" + URLEncoder.encode(item.name, "UTF-8") + "/$i",
                price = Price.round2(priceEur * rate),
                currency = currency,
                shipping = s.ship?.let { RawShipping(it, "EUR", s.country == settings.homeCountry) },
                shopCountry = s.country,
                condition = "new",
            )
        }
        return ProviderResult(offers)
    }
}
