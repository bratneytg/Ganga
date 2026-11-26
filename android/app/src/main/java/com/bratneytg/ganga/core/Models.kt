package com.bratneytg.ganga.core

data class ShippingRates(
    val domestic: Double = 4.99,
    val eu: Double = 9.99,
    val nonEu: Double = 19.99,
    val importPct: Double = 19.0,
)

data class Settings(
    val homeCountry: String = "DE",
    val homeZip: String = "",
    val searchCountries: List<String> = listOf("DE", "ES", "FR", "IT", "NL"),
    val scheduleTimes: List<String> = listOf("00:00", "06:00", "12:00", "18:00"),
    val serperApiKey: String = "",
    val ebayClientId: String = "",
    val ebayClientSecret: String = "",
    val ebayMarketplaces: List<String> = listOf("EBAY_DE", "EBAY_ES", "EBAY_FR", "EBAY_IT"),
    val demoMode: Boolean = false,
    val shipping: ShippingRates = ShippingRates(),
)

data class HistoryPoint(
    val t: Long,
    val landed: Double,
    val price: Double,
    val store: String,
    val country: String,
)

/** Oferta ya procesada: todo en euros y con el precio total calculado. */
data class Offer(
    val id: String,
    val provider: String,
    val store: String,
    val title: String,
    val url: String,
    val price: Double,
    val currency: String,
    val priceEur: Double,
    val shippingEur: Double,
    val shippingEstimated: Boolean,
    val importEur: Double,
    val landedEur: Double,
    val country: String,
    val foreign: Boolean,
    val condition: String,
    val unavailable: Boolean = false,
    val suspicious: Boolean = false,
)

data class RawShipping(val amount: Double, val currency: String?, val trusted: Boolean)

/** Oferta tal como la entrega un proveedor, antes de filtrar y convertir. */
data class RawOffer(
    val provider: String,
    val store: String,
    val title: String,
    val url: String,
    val price: Double,
    val currency: String,
    val shipping: RawShipping? = null,
    val shopCountry: String? = null,
    val searchedCountry: String? = null,
    val condition: String? = null,
    val unavailable: Boolean = false,
)

data class Item(
    val id: String,
    val name: String,
    val query: String,
    val condition: String = "new", // new | used | any
    val mustInclude: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
    val filterAccessories: Boolean = true,
    val targetPrice: Double? = null,
    val minPrice: Double? = null,
    val countries: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
    val notes: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
    val lastCheckedAt: Long? = null,
    val lastError: String? = null,
    val offers: List<Offer> = emptyList(),
    val best: Offer? = null,
    val medianLanded: Double? = null,
    val history: List<HistoryPoint> = emptyList(),
    val lastNotifiedLanded: Double? = null,
)
