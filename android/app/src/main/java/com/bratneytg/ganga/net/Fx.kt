package com.bratneytg.ganga.net

import com.bratneytg.ganga.core.Rates
import org.json.JSONObject
import java.io.File

/** Tipos de cambio del BCE (vía frankfurter.dev), guardados en disco y con valores de reserva. */
class Fx(private val file: File? = null) : Rates {
    @Volatile
    var rates: Map<String, Double> = FALLBACK
        private set

    @Volatile
    var updatedAt: Long? = null
        private set

    @Volatile
    var live: Boolean = false
        private set

    init {
        try {
            if (file != null && file.exists()) {
                val json = JSONObject(file.readText())
                rates = FALLBACK + parseRates(json.getJSONObject("rates"))
                updatedAt = json.optLong("updatedAt").takeIf { it > 0 }
                live = true
            }
        } catch (e: Exception) {
            // archivo dañado: se usan los de reserva
        }
    }

    override fun toEur(amount: Double, currency: String): Double {
        val rate = rates[currency] ?: return Double.NaN
        return amount / rate
    }

    /** Actualiza los tipos si tienen más de 20 horas. Nunca lanza: si falla, usa los guardados. */
    suspend fun refresh() {
        val last = updatedAt
        if (last != null && System.currentTimeMillis() - last < MAX_AGE_MS) return
        try {
            val body = Http.request("https://api.frankfurter.dev/v1/latest?base=EUR", timeoutMs = 10_000)
            val parsed = parseRates(JSONObject(body).getJSONObject("rates"))
            if (parsed.isNotEmpty()) {
                rates = FALLBACK + parsed + ("EUR" to 1.0)
                updatedAt = System.currentTimeMillis()
                live = true
                file?.writeText(JSONObject().put("rates", JSONObject(rates)).put("updatedAt", updatedAt).toString())
            }
        } catch (e: Exception) {
            // sin red: se mantienen los tipos que hubiera
        }
    }

    private fun parseRates(obj: JSONObject): Map<String, Double> {
        val out = HashMap<String, Double>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.optDouble(k)
            if (!v.isNaN() && v > 0) out[k] = v
        }
        return out
    }

    companion object {
        private const val MAX_AGE_MS = 20L * 3600 * 1000

        // Aproximados, por si no hay conexión (unidades de moneda por 1 EUR).
        val FALLBACK: Map<String, Double> = mapOf(
            "EUR" to 1.0, "USD" to 1.1, "GBP" to 0.85, "PLN" to 4.3, "SEK" to 11.3, "DKK" to 7.46,
            "NOK" to 11.6, "CHF" to 0.95, "CZK" to 25.0, "HUF" to 400.0, "RON" to 4.97,
        )
    }
}
