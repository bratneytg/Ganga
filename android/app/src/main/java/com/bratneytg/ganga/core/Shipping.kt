package com.bratneytg.ganga.core

data class Delivery(val amount: Double, val currency: String?)
data class ShippingEstimate(val amount: Double, val region: String)

object Shipping {
    private val freeRegex = Regex(
        "gratis|gratuit|free|kostenlos|kostenfrei|versandkostenfrei|sin coste|incluido|inclus|inclusa|gratuita|besplatn|darmow",
        RegexOption.IGNORE_CASE,
    )

    /** Interpreta textos como "Envío gratis", "+ 4,99 € de envío" o "Get it by Wed (Free)". */
    fun parseDelivery(text: String?): Delivery? {
        if (text.isNullOrBlank()) return null
        if (freeRegex.containsMatchIn(text)) return Delivery(0.0, null)
        val p = Price.parse(text) ?: return null
        return Delivery(p.value, p.currency)
    }

    /** Estimación (en EUR) cuando la tienda no dice cuánto cuesta el envío. */
    fun estimate(shopCountry: String?, destCountry: String, rates: ShippingRates): ShippingEstimate = when {
        shopCountry == null || shopCountry == destCountry -> ShippingEstimate(rates.domestic, "domestic")
        Countries.isEu(shopCountry) && Countries.isEu(destCountry) -> ShippingEstimate(rates.eu, "eu")
        else -> ShippingEstimate(rates.nonEu, "nonEu")
    }

    /** IVA/aduana estimados si la tienda está fuera de la UE y tú dentro. */
    fun estimateImport(shopCountry: String?, destCountry: String, baseEur: Double, pct: Double): Double {
        if (shopCountry == null || Countries.isEu(shopCountry) || !Countries.isEu(destCountry)) return 0.0
        return baseEur * (pct / 100.0)
    }
}
