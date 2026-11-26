package com.bratneytg.ganga.core

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

fun interface Rates {
    /** Convierte [amount] de [currency] a euros. NaN si no hay tipo de cambio. */
    fun toEur(amount: Double, currency: String): Double
}

data class Decision(val reasons: List<String>, val lastNotified: Double?)

/**
 * Convierte las ofertas "crudas" de los proveedores en ofertas comparables:
 * filtra lo irrelevante, pasa todo a EUR y calcula precio + envío + importación.
 * Todo son funciones puras (sin red ni Android), por eso se pueden probar con JUnit.
 */
object Engine {
    const val MAX_OFFERS = 40
    const val MAX_HISTORY = 1500
    private val direct = setOf("urlwatch", "demo") // no necesitan filtro de relevancia
    private val googleHost = Regex("(^|\\.)google\\.")

    fun median(nums: List<Double>): Double? {
        if (nums.isEmpty()) return null
        val s = nums.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
    }

    private fun dedupeKey(o: RawOffer, priceEur: Double): String {
        try {
            val uri = URI(o.url)
            val host = uri.host
            if (host != null && !googleHost.containsMatchIn(host)) return host + (uri.path ?: "")
        } catch (e: Exception) {
            // URL rara: se usa la clave de abajo
        }
        return o.store + "|" + String.format(Locale.ROOT, "%.2f", priceEur) + "|" + Match.compact(o.title).take(30)
    }

    private fun sha1Short(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(12)
    }

    fun processOffers(raw: List<RawOffer>, item: Item, settings: Settings, rates: Rates): List<Offer> {
        val dest = settings.homeCountry
        val byKey = LinkedHashMap<String, Offer>()

        for (o in raw) {
            if (o.price <= 0.0) continue
            val priceEur = rates.toEur(o.price, o.currency.ifEmpty { "EUR" })
            if (priceEur.isNaN() || priceEur <= 0.0) continue
            if (o.provider !in direct && !Match.isRelevant(o.title, item)) continue
            if (item.minPrice != null && priceEur < item.minPrice) continue

            val condition = o.condition ?: Match.detectCondition(o.title)
            if (item.condition == "new" && condition == "used") continue
            if (item.condition == "used" && condition == "new") continue

            val shopCountry = o.shopCountry ?: dest
            val ship = o.shipping
            val shippingEur: Double
            val shippingEstimated: Boolean
            if (ship != null && ship.trusted) {
                shippingEur = rates.toEur(ship.amount, ship.currency ?: "EUR")
                shippingEstimated = false
            } else {
                shippingEur = Shipping.estimate(shopCountry, dest, settings.shipping).amount
                shippingEstimated = true
            }
            if (shippingEur.isNaN()) continue
            val importEur = Shipping.estimateImport(shopCountry, dest, priceEur + shippingEur, settings.shipping.importPct)
            val landed = priceEur + shippingEur + importEur

            val key = dedupeKey(o, priceEur)
            val prev = byKey[key]
            if (prev != null && prev.landedEur <= landed) continue

            byKey[key] = Offer(
                id = sha1Short(key),
                provider = o.provider,
                store = o.store,
                title = o.title,
                url = o.url,
                price = o.price,
                currency = o.currency.ifEmpty { "EUR" },
                priceEur = Price.round2(priceEur),
                shippingEur = Price.round2(shippingEur),
                shippingEstimated = shippingEstimated,
                importEur = Price.round2(importEur),
                landedEur = Price.round2(landed),
                country = shopCountry,
                foreign = shopCountry != dest,
                condition = condition,
                unavailable = o.unavailable,
            )
        }

        var offers = byKey.values.toList()

        // Un precio muy por debajo de la mediana suele ser un accesorio, un anuncio engañoso o un error.
        val comparable = offers.filter { it.provider !in direct && !it.unavailable }
        if (comparable.size >= 5) {
            val med = median(comparable.map { it.landedEur })!!
            offers = offers.map {
                if (it.provider !in direct && !it.unavailable && it.landedEur < med * 0.4) it.copy(suspicious = true) else it
            }
        }

        return offers
            .sortedWith(compareBy<Offer>({ it.unavailable }, { it.suspicious }, { it.landedEur }))
            .take(MAX_OFFERS)
    }

    /** La mejor oferta válida (disponible y no sospechosa), o null. */
    fun best(offers: List<Offer>): Offer? = offers.firstOrNull { !it.unavailable && !it.suspicious }

    /** ¿Hay que avisar? Motivos: "target" (bajo tu objetivo) y "lowest" (mínimo histórico). */
    fun decideNotification(target: Double?, lastNotified: Double?, bestLanded: Double, prior: List<Double>): Decision {
        var last = lastNotified
        if (last != null && bestLanded > last * 1.05) last = null // el precio rebotó: vuelve a avisar si baja
        val reasons = mutableListOf<String>()
        if (target != null && bestLanded <= target) reasons.add("target")
        if (prior.size >= 3 && bestLanded < prior.min() * 0.98) reasons.add("lowest")
        if (reasons.isEmpty()) return Decision(emptyList(), last)
        if (last != null && bestLanded >= last * 0.995) return Decision(emptyList(), last) // ya avisado a este precio
        return Decision(reasons, last)
    }
}
