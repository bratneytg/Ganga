package com.bratneytg.ganga.providers

import com.bratneytg.ganga.core.Countries
import com.bratneytg.ganga.core.Item
import com.bratneytg.ganga.core.Price
import com.bratneytg.ganga.core.RawOffer
import com.bratneytg.ganga.core.RawShipping
import com.bratneytg.ganga.core.Settings
import com.bratneytg.ganga.core.Shipping
import com.bratneytg.ganga.net.Fx
import com.bratneytg.ganga.net.Http
import com.bratneytg.ganga.net.HttpError
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Google Shopping a través de Serper.dev: una sola consulta devuelve ofertas de muchísimas tiendas.
 * Se consulta una vez por país configurado (gl=de, es, fr...) para cubrir tiendas de toda Europa.
 */
object SerperProvider : Provider {
    override val id = "serper"
    override val name = "Google Shopping (Serper)"
    private const val ENDPOINT = "https://google.serper.dev/shopping"

    override fun isConfigured(settings: Settings) = settings.serperApiKey.isNotBlank()

    // Google usa "uk" y no "gb" en el parámetro gl.
    private fun glFor(country: String) = if (country == "GB") "uk" else country.lowercase()

    private fun JSONObject.text(vararg keys: String): String? {
        for (k in keys) {
            val v = opt(k)
            if (v != null && v != JSONObject.NULL && v.toString().isNotBlank()) return v.toString()
        }
        return null
    }

    fun normalizeRow(row: JSONObject, searchedCountry: String, homeCountry: String): RawOffer? {
        val title = row.text("title", "name") ?: return null
        val url = row.text("link", "product_link", "productLink") ?: return null
        val priceRaw = row.opt("price").takeIf { it != null && it != JSONObject.NULL }
            ?: row.opt("extracted_price").takeIf { it != null && it != JSONObject.NULL }
            ?: return null

        val defaultCurrency = Countries.get(searchedCountry)?.currency ?: "EUR"
        val parsed = Price.fromAny(priceRaw, defaultCurrency) ?: return null

        val shopCountry = Countries.fromUrl(url) ?: searchedCountry
        val delivery = Shipping.parseDelivery(row.text("delivery", "shipping"))
        val store = row.text("source", "seller")
            ?: try {
                URI(url).host?.removePrefix("www.") ?: "tienda"
            } catch (e: Exception) {
                "tienda"
            }
        val used = row.text("secondHandCondition", "second_hand_condition") != null

        return RawOffer(
            provider = "serper",
            store = store,
            title = title,
            url = url,
            price = parsed.value,
            currency = parsed.currency ?: defaultCurrency,
            // El "envío gratis" de Google solo vale para quien busca desde ese país.
            shipping = delivery?.let {
                RawShipping(it.amount, it.currency ?: defaultCurrency, shopCountry == homeCountry || searchedCountry == homeCountry)
            },
            shopCountry = shopCountry,
            searchedCountry = searchedCountry,
            condition = if (used) "used" else null,
        )
    }

    override suspend fun search(item: Item, settings: Settings, fx: Fx): ProviderResult {
        val countries = (if (item.countries.isNotEmpty()) item.countries else settings.searchCountries)
            .filter { Countries.get(it) != null }
        val offers = ArrayList<RawOffer>()
        val warnings = ArrayList<String>()
        var failures = 0

        for (country in countries) {
            try {
                val payload = JSONObject()
                    .put("q", item.query)
                    .put("gl", glFor(country))
                    .put("hl", Countries.get(country)?.lang ?: "en")
                    .put("num", 40)
                val body = Http.request(
                    ENDPOINT,
                    method = "POST",
                    headers = mapOf("X-API-KEY" to settings.serperApiKey, "Content-Type" to "application/json"),
                    body = payload.toString(),
                )
                val json = JSONObject(body)
                val rows: JSONArray = json.optJSONArray("shopping") ?: json.optJSONArray("shopping_results") ?: JSONArray()
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    normalizeRow(row, country, settings.homeCountry)?.let { offers.add(it) }
                }
            } catch (e: HttpError) {
                failures++
                warnings.add("Serper ($country): ${e.message}")
                if (e.status == 401 || e.status == 403) break // clave inválida: no insistir con los demás países
            } catch (e: org.json.JSONException) {
                failures++
                warnings.add("Serper ($country): respuesta no válida")
            }
            delay(250)
        }
        if (countries.isNotEmpty() && failures == countries.size) throw HttpError(null, warnings.firstOrNull() ?: "Serper no respondió")
        return ProviderResult(offers, warnings)
    }
}
