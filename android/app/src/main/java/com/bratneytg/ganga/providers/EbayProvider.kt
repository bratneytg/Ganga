package com.bratneytg.ganga.providers

import android.util.Base64
import com.bratneytg.ganga.core.EbayMarkets
import com.bratneytg.ganga.core.Item
import com.bratneytg.ganga.core.RawOffer
import com.bratneytg.ganga.core.RawShipping
import com.bratneytg.ganga.core.Settings
import com.bratneytg.ganga.net.Fx
import com.bratneytg.ganga.net.Http
import com.bratneytg.ganga.net.HttpError
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URLEncoder

/**
 * eBay Browse API (gratuita con una cuenta de desarrollador). Da precio real, país del vendedor
 * y coste de envío hacia tu país, así que es la fuente más precisa del coste total.
 */
object EbayProvider : Provider {
    override val id = "ebay"
    override val name = "eBay"
    private const val API = "https://api.ebay.com"

    private var tokenKey: String? = null
    private var token: String? = null
    private var tokenExpiresAt = 0L

    override fun isConfigured(settings: Settings) = settings.ebayClientId.isNotBlank() && settings.ebayClientSecret.isNotBlank()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun getToken(settings: Settings): String {
        val key = settings.ebayClientId + ":" + settings.ebayClientSecret
        val cached = token
        if (cached != null && tokenKey == key && System.currentTimeMillis() < tokenExpiresAt) return cached
        val basic = Base64.encodeToString(key.toByteArray(), Base64.NO_WRAP)
        val body = Http.request(
            "$API/identity/v1/oauth2/token",
            method = "POST",
            headers = mapOf("Authorization" to "Basic $basic", "Content-Type" to "application/x-www-form-urlencoded"),
            body = "grant_type=client_credentials&scope=" + enc("$API/oauth/api_scope"),
        )
        val json = JSONObject(body)
        val t = json.getString("access_token")
        token = t
        tokenKey = key
        tokenExpiresAt = System.currentTimeMillis() + (json.optLong("expires_in", 7200) - 120) * 1000
        return t
    }

    /** El envío más barato de los que ofrece el artículo. */
    private fun cheapestShipping(item: JSONObject): Pair<Double, String>? {
        val options = item.optJSONArray("shippingOptions") ?: return null
        var best: Pair<Double, String>? = null
        for (i in 0 until options.length()) {
            val cost = options.optJSONObject(i)?.optJSONObject("shippingCost") ?: continue
            val value = cost.optString("value").toDoubleOrNull() ?: continue
            val currency = cost.optString("currency", "EUR")
            if (best == null || value < best.first) best = value to currency
        }
        return best
    }

    fun normalizeItem(obj: JSONObject, marketplaceCountry: String): RawOffer? {
        val title = obj.optString("title")
        val url = obj.optString("itemWebUrl")
        val priceObj = obj.optJSONObject("price") ?: return null
        val price = priceObj.optString("value").toDoubleOrNull() ?: return null
        if (title.isBlank() || url.isBlank()) return null
        val ship = cheapestShipping(obj)
        val condition = obj.optString("condition")
        val used = Regex("used|usado|gebraucht|refurb|certified|for parts", RegexOption.IGNORE_CASE).containsMatchIn(condition)
        val seller = obj.optJSONObject("seller")?.optString("username").orEmpty()
        return RawOffer(
            provider = "ebay",
            store = if (seller.isNotEmpty()) "eBay · $seller" else "eBay",
            title = title,
            url = url,
            price = price,
            currency = priceObj.optString("currency", "EUR"),
            shipping = ship?.let { RawShipping(it.first, it.second, true) },
            shopCountry = obj.optJSONObject("itemLocation")?.optString("country")?.takeIf { c -> c.isNotBlank() } ?: marketplaceCountry,
            searchedCountry = marketplaceCountry,
            condition = if (used) "used" else "new",
        )
    }

    override suspend fun search(item: Item, settings: Settings, fx: Fx): ProviderResult {
        val token = getToken(settings)
        val marketplaces = settings.ebayMarketplaces.filter { EbayMarkets.countryOf.containsKey(it) }
        val offers = ArrayList<RawOffer>()
        val warnings = ArrayList<String>()
        var failures = 0

        val filters = mutableListOf("buyingOptions:{FIXED_PRICE}")
        if (item.condition == "new") filters.add("conditions:{NEW}")
        if (item.condition == "used") filters.add("conditions:{USED}")

        // Contexto del comprador: eBay calcula el envío hacia este país (y código postal si lo das).
        val ctx = "country=${settings.homeCountry}" + if (settings.homeZip.isNotBlank()) ",zip=${settings.homeZip}" else ""

        for (marketplace in marketplaces) {
            try {
                val url = "$API/buy/browse/v1/item_summary/search?q=${enc(item.query)}&limit=50&sort=price&filter=${enc(filters.joinToString(","))}"
                val body = Http.request(
                    url,
                    headers = mapOf(
                        "Authorization" to "Bearer $token",
                        "X-EBAY-C-MARKETPLACE-ID" to marketplace,
                        "X-EBAY-C-ENDUSERCTX" to "contextualLocation=" + enc(ctx),
                    ),
                )
                val summaries = JSONObject(body).optJSONArray("itemSummaries")
                if (summaries != null) {
                    for (i in 0 until summaries.length()) {
                        val obj = summaries.optJSONObject(i) ?: continue
                        normalizeItem(obj, EbayMarkets.countryOf.getValue(marketplace))?.let { offers.add(it) }
                    }
                }
            } catch (e: HttpError) {
                failures++
                warnings.add("eBay ($marketplace): ${e.message}")
                if (e.status == 401 || e.status == 403) break
            } catch (e: org.json.JSONException) {
                failures++
                warnings.add("eBay ($marketplace): respuesta no válida")
            }
            delay(250)
        }
        if (marketplaces.isNotEmpty() && failures == marketplaces.size) throw HttpError(null, warnings.firstOrNull() ?: "eBay no respondió")
        return ProviderResult(offers, warnings)
    }
}
