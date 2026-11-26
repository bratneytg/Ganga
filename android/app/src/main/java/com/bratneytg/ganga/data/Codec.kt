package com.bratneytg.ganga.data

import com.bratneytg.ganga.core.HistoryPoint
import com.bratneytg.ganga.core.Item
import com.bratneytg.ganga.core.Offer
import com.bratneytg.ganga.core.Settings
import com.bratneytg.ganga.core.ShippingRates
import org.json.JSONArray
import org.json.JSONObject

data class Meta(val lastRunAt: Long? = null, val lastRunTrigger: String? = null)

data class AppData(
    val settings: Settings = Settings(),
    val items: List<Item> = emptyList(),
    val meta: Meta = Meta(),
)

/** Conversión de los datos de la app a JSON (y vuelta) para guardarlos en un archivo. */
object Codec {
    private fun JSONObject.str(key: String, default: String = ""): String =
        if (has(key) && !isNull(key)) optString(key, default) else default

    private fun JSONObject.strOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).ifEmpty { null } else null

    private fun JSONObject.dblOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun JSONObject.lngOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun strings(arr: JSONArray?): List<String> =
        if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it) }

    private fun toArray(list: List<String>): JSONArray = JSONArray().also { a -> list.forEach { a.put(it) } }

    // ---------- ajustes ----------

    fun settingsToJson(s: Settings): JSONObject = JSONObject()
        .put("homeCountry", s.homeCountry)
        .put("homeZip", s.homeZip)
        .put("searchCountries", toArray(s.searchCountries))
        .put("scheduleTimes", toArray(s.scheduleTimes))
        .put("serperApiKey", s.serperApiKey)
        .put("ebayClientId", s.ebayClientId)
        .put("ebayClientSecret", s.ebayClientSecret)
        .put("ebayMarketplaces", toArray(s.ebayMarketplaces))
        .put("demoMode", s.demoMode)
        .put(
            "shipping",
            JSONObject()
                .put("domestic", s.shipping.domestic)
                .put("eu", s.shipping.eu)
                .put("nonEu", s.shipping.nonEu)
                .put("importPct", s.shipping.importPct),
        )

    fun settingsFromJson(j: JSONObject): Settings {
        val d = Settings()
        val ship = j.optJSONObject("shipping")
        val dr = ShippingRates()
        return Settings(
            homeCountry = j.str("homeCountry", d.homeCountry),
            homeZip = j.str("homeZip"),
            searchCountries = j.optJSONArray("searchCountries")?.let { strings(it) } ?: d.searchCountries,
            scheduleTimes = j.optJSONArray("scheduleTimes")?.let { strings(it) } ?: d.scheduleTimes,
            serperApiKey = j.str("serperApiKey"),
            ebayClientId = j.str("ebayClientId"),
            ebayClientSecret = j.str("ebayClientSecret"),
            ebayMarketplaces = j.optJSONArray("ebayMarketplaces")?.let { strings(it) } ?: d.ebayMarketplaces,
            demoMode = j.optBoolean("demoMode", false),
            shipping = ShippingRates(
                domestic = ship?.dblOrNull("domestic") ?: dr.domestic,
                eu = ship?.dblOrNull("eu") ?: dr.eu,
                nonEu = ship?.dblOrNull("nonEu") ?: dr.nonEu,
                importPct = ship?.dblOrNull("importPct") ?: dr.importPct,
            ),
        )
    }

    // ---------- ofertas e historial ----------

    private fun offerToJson(o: Offer): JSONObject = JSONObject()
        .put("id", o.id).put("provider", o.provider).put("store", o.store).put("title", o.title).put("url", o.url)
        .put("price", o.price).put("currency", o.currency).put("priceEur", o.priceEur)
        .put("shippingEur", o.shippingEur).put("shippingEstimated", o.shippingEstimated)
        .put("importEur", o.importEur).put("landedEur", o.landedEur).put("country", o.country)
        .put("foreign", o.foreign).put("condition", o.condition)
        .put("unavailable", o.unavailable).put("suspicious", o.suspicious)

    private fun offerFromJson(j: JSONObject): Offer = Offer(
        id = j.str("id"),
        provider = j.str("provider"),
        store = j.str("store"),
        title = j.str("title"),
        url = j.str("url"),
        price = j.optDouble("price"),
        currency = j.str("currency", "EUR"),
        priceEur = j.optDouble("priceEur"),
        shippingEur = j.optDouble("shippingEur"),
        shippingEstimated = j.optBoolean("shippingEstimated"),
        importEur = j.optDouble("importEur"),
        landedEur = j.optDouble("landedEur"),
        country = j.str("country"),
        foreign = j.optBoolean("foreign"),
        condition = j.str("condition", "new"),
        unavailable = j.optBoolean("unavailable"),
        suspicious = j.optBoolean("suspicious"),
    )

    private fun historyToJson(list: List<HistoryPoint>): JSONArray = JSONArray().also { arr ->
        list.forEach { h ->
            arr.put(JSONArray().put(h.t).put(h.landed).put(h.price).put(h.store).put(h.country))
        }
    }

    private fun historyFromJson(arr: JSONArray?): List<HistoryPoint> {
        if (arr == null) return emptyList()
        val out = ArrayList<HistoryPoint>(arr.length())
        for (i in 0 until arr.length()) {
            val p = arr.optJSONArray(i) ?: continue
            out.add(HistoryPoint(p.optLong(0), p.optDouble(1), p.optDouble(2), p.optString(3), p.optString(4)))
        }
        return out
    }

    // ---------- artículos ----------

    fun itemToJson(i: Item): JSONObject {
        val j = JSONObject()
            .put("id", i.id).put("name", i.name).put("query", i.query).put("condition", i.condition)
            .put("mustInclude", toArray(i.mustInclude)).put("exclude", toArray(i.exclude))
            .put("filterAccessories", i.filterAccessories)
            .put("countries", toArray(i.countries)).put("urls", toArray(i.urls))
            .put("notes", i.notes).put("enabled", i.enabled).put("createdAt", i.createdAt)
            .put("offers", JSONArray().also { a -> i.offers.forEach { a.put(offerToJson(it)) } })
            .put("history", historyToJson(i.history))
        i.targetPrice?.let { j.put("targetPrice", it) }
        i.minPrice?.let { j.put("minPrice", it) }
        i.lastCheckedAt?.let { j.put("lastCheckedAt", it) }
        i.lastError?.let { j.put("lastError", it) }
        i.best?.let { j.put("best", offerToJson(it)) }
        i.medianLanded?.let { j.put("medianLanded", it) }
        i.lastNotifiedLanded?.let { j.put("lastNotifiedLanded", it) }
        return j
    }

    fun itemFromJson(j: JSONObject): Item {
        val offersArr = j.optJSONArray("offers")
        val offers = if (offersArr == null) emptyList() else (0 until offersArr.length()).mapNotNull { offersArr.optJSONObject(it)?.let { offerFromJson(it) } }
        return Item(
            id = j.str("id"),
            name = j.str("name"),
            query = j.str("query", j.str("name")),
            condition = j.str("condition", "new"),
            mustInclude = strings(j.optJSONArray("mustInclude")),
            exclude = strings(j.optJSONArray("exclude")),
            filterAccessories = j.optBoolean("filterAccessories", true),
            targetPrice = j.dblOrNull("targetPrice"),
            minPrice = j.dblOrNull("minPrice"),
            countries = strings(j.optJSONArray("countries")),
            urls = strings(j.optJSONArray("urls")),
            notes = j.str("notes"),
            enabled = j.optBoolean("enabled", true),
            createdAt = j.optLong("createdAt"),
            lastCheckedAt = j.lngOrNull("lastCheckedAt"),
            lastError = j.strOrNull("lastError"),
            offers = offers,
            best = j.optJSONObject("best")?.let { offerFromJson(it) },
            medianLanded = j.dblOrNull("medianLanded"),
            history = historyFromJson(j.optJSONArray("history")),
            lastNotifiedLanded = j.dblOrNull("lastNotifiedLanded"),
        )
    }

    // ---------- todo ----------

    fun toJson(d: AppData): JSONObject = JSONObject()
        .put("settings", settingsToJson(d.settings))
        .put("items", JSONArray().also { a -> d.items.forEach { a.put(itemToJson(it)) } })
        .put(
            "meta",
            JSONObject().also { m ->
                d.meta.lastRunAt?.let { m.put("lastRunAt", it) }
                d.meta.lastRunTrigger?.let { m.put("lastRunTrigger", it) }
            },
        )

    fun fromJson(j: JSONObject): AppData {
        val itemsArr = j.optJSONArray("items")
        val items = if (itemsArr == null) emptyList() else (0 until itemsArr.length()).mapNotNull { itemsArr.optJSONObject(it)?.let { itemFromJson(it) } }
        val meta = j.optJSONObject("meta")
        return AppData(
            settings = j.optJSONObject("settings")?.let { settingsFromJson(it) } ?: Settings(),
            items = items,
            meta = Meta(meta?.lngOrNull("lastRunAt"), meta?.strOrNull("lastRunTrigger")),
        )
    }
}
