package com.bratneytg.ganga.providers

import com.bratneytg.ganga.core.Countries
import com.bratneytg.ganga.core.Item
import com.bratneytg.ganga.core.Price
import com.bratneytg.ganga.core.RawOffer
import com.bratneytg.ganga.core.RawShipping
import com.bratneytg.ganga.core.Settings
import com.bratneytg.ganga.net.Fx
import com.bratneytg.ganga.net.Http
import com.bratneytg.ganga.net.HttpError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.InetAddress
import java.net.URI

/**
 * Vigila páginas de producto concretas (la ficha de una tienda). No necesita claves: lee los
 * datos estructurados (JSON-LD de schema.org) o las metaetiquetas de precio. No funciona en las
 * tiendas que bloquean robots (p. ej. Amazon).
 */
object UrlWatchProvider : Provider {
    override val id = "urlwatch"
    override val name = "Páginas concretas (URL)"
    override val needsUrls = true

    override fun isConfigured(settings: Settings) = true

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml",
        "Accept-Language" to "de,es;q=0.8,en;q=0.6",
    )

    data class Extracted(
        val title: String,
        val price: Double,
        val currency: String?,
        val available: Boolean,
        val shipping: Double?,
        val shippingCurrency: String?,
    )

    private class Candidate(
        val price: Double,
        val currency: String?,
        val available: Boolean,
        val shipping: Double?,
        val shippingCurrency: String?,
    )

    private fun JSONObject.optAny(key: String): Any? = opt(key).takeIf { it != null && it != JSONObject.NULL }

    private fun walk(node: Any?, visit: (JSONObject) -> Unit) {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
            is JSONObject -> {
                visit(node)
                walk(node.opt("@graph"), visit)
            }
            else -> Unit
        }
    }

    private fun typesOf(node: JSONObject): List<String> {
        val t = node.opt("@type")
        return when (t) {
            null -> emptyList()
            is JSONArray -> (0 until t.length()).map { t.optString(it) }
            else -> listOf(t.toString())
        }
    }

    private val soldOut = Regex("OutOfStock|SoldOut|Discontinued", RegexOption.IGNORE_CASE)

    private fun candidateOf(offer: JSONObject): Candidate? {
        val spec = offer.optJSONObject("priceSpecification")
        val raw = offer.optAny("price") ?: offer.optAny("lowPrice") ?: spec?.optAny("price")
        val currency = offer.optString("priceCurrency").ifBlank { spec?.optString("priceCurrency").orEmpty() }.ifBlank { null }
        val parsed = Price.fromAny(raw, currency) ?: return null
        val rate = offer.optJSONObject("shippingDetails")?.optJSONObject("shippingRate")
        val shipping = rate?.optAny("value")?.let { Price.fromAny(it)?.value }
        return Candidate(
            price = parsed.value,
            currency = parsed.currency,
            available = !soldOut.containsMatchIn(offer.optString("availability")),
            shipping = shipping,
            shippingCurrency = rate?.optString("currency")?.ifBlank { null },
        )
    }

    /** Extrae título, precio, moneda y disponibilidad de una página de producto. */
    fun extractFromHtml(html: String, url: String): Extracted? {
        val doc = Jsoup.parse(html)
        var title: String? = null
        val found = ArrayList<Candidate>()

        for (script in doc.select("script[type=\"application/ld+json\"]")) {
            val json: Any = try {
                val text = script.data().trim()
                if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
            } catch (e: JSONException) {
                continue
            }
            walk(json) { node ->
                val types = typesOf(node)
                if (types.contains("Product")) {
                    if (title == null) title = node.optString("name").ifBlank { null }
                    walk(node.opt("offers")) { o -> candidateOf(o)?.let { found.add(it) } }
                } else if (types.any { it.endsWith("Offer") }) {
                    candidateOf(node)?.let { found.add(it) }
                }
            }
        }

        if (found.isEmpty()) {
            val amount = doc.selectFirst("meta[property=\"product:price:amount\"]")?.attr("content")
                ?: doc.selectFirst("meta[property=\"og:price:amount\"]")?.attr("content")
                ?: doc.selectFirst("meta[itemprop=\"price\"]")?.attr("content")
            val currency = doc.selectFirst("meta[property=\"product:price:currency\"]")?.attr("content")
                ?: doc.selectFirst("meta[property=\"og:price:currency\"]")?.attr("content")
                ?: doc.selectFirst("meta[itemprop=\"priceCurrency\"]")?.attr("content")
            val parsed = Price.parse(amount, currency?.ifBlank { null }) ?: return null
            found.add(Candidate(parsed.value, parsed.currency, true, null, null))
        }

        // Entre varias ofertas de la misma ficha, la más barata que esté disponible.
        val inStock = found.filter { it.available }
        val best = (if (inStock.isNotEmpty()) inStock else found).minByOrNull { it.price } ?: return null
        val finalTitle = title
            ?: doc.selectFirst("meta[property=\"og:title\"]")?.attr("content")?.ifBlank { null }
            ?: doc.title().ifBlank { null }
            ?: url
        return Extracted(finalTitle.trim().take(200), best.price, best.currency, best.available, best.shipping, best.shippingCurrency)
    }

    private fun isPrivate(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress) return true
        val raw = addr.address
        return raw.size == 16 && (raw[0].toInt() and 0xFE) == 0xFC // fc00::/7
    }

    /** Solo http(s) hacia internet público: evita que la app se use para tocar tu red local. */
    private suspend fun assertPublicUrl(raw: String): URI {
        val uri = try {
            URI(raw.trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("URL no válida")
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if ((scheme != "http" && scheme != "https") || host == null) throw IllegalArgumentException("Solo se admiten URLs http(s)")
        if (host == "localhost" || host.endsWith(".local")) throw IllegalArgumentException("No se admiten direcciones locales")
        val addrs = withContext(Dispatchers.IO) {
            try {
                InetAddress.getAllByName(host).toList()
            } catch (e: Exception) {
                throw IllegalArgumentException("No se encuentra el sitio $host")
            }
        }
        if (addrs.any { isPrivate(it) }) throw IllegalArgumentException("No se admiten direcciones de red local")
        return uri
    }

    override suspend fun search(item: Item, settings: Settings, fx: Fx): ProviderResult {
        val offers = ArrayList<RawOffer>()
        val warnings = ArrayList<String>()
        for (entry in item.urls) {
            try {
                val uri = assertPublicUrl(entry)
                val host = uri.host.removePrefix("www.")
                val html = Http.request(uri.toString(), headers = headers, timeoutMs = 25_000)
                val data = extractFromHtml(html, uri.toString())
                if (data == null) {
                    warnings.add("$host: no encontré el precio en la página (puede bloquear robots)")
                    continue
                }
                val shopCountry = Countries.fromUrl(uri.toString()) ?: settings.homeCountry
                offers.add(
                    RawOffer(
                        provider = "urlwatch",
                        store = host,
                        title = data.title,
                        url = uri.toString(),
                        price = data.price,
                        currency = data.currency ?: "EUR",
                        shipping = data.shipping?.let { RawShipping(it, data.shippingCurrency ?: data.currency ?: "EUR", shopCountry == settings.homeCountry) },
                        shopCountry = shopCountry,
                        condition = "unknown",
                        unavailable = !data.available,
                    ),
                )
            } catch (e: HttpError) {
                warnings.add("${entry.take(50)}: ${e.message}")
            } catch (e: IllegalArgumentException) {
                warnings.add("${entry.take(50)}: ${e.message}")
            }
        }
        return ProviderResult(offers, warnings)
    }
}
