package com.bratneytg.ganga.core

import java.net.URI

data class Country(val code: String, val name: String, val currency: String, val lang: String, val eu: Boolean)

/** Países que Ganga sabe tratar: nombre, moneda e idioma de búsqueda. */
object Countries {
    val all: List<Country> = listOf(
        Country("DE", "Alemania", "EUR", "de", true),
        Country("ES", "España", "EUR", "es", true),
        Country("FR", "Francia", "EUR", "fr", true),
        Country("IT", "Italia", "EUR", "it", true),
        Country("NL", "Países Bajos", "EUR", "nl", true),
        Country("BE", "Bélgica", "EUR", "nl", true),
        Country("AT", "Austria", "EUR", "de", true),
        Country("PT", "Portugal", "EUR", "pt", true),
        Country("IE", "Irlanda", "EUR", "en", true),
        Country("PL", "Polonia", "PLN", "pl", true),
        Country("SE", "Suecia", "SEK", "sv", true),
        Country("DK", "Dinamarca", "DKK", "da", true),
        Country("CZ", "Chequia", "CZK", "cs", true),
        Country("GB", "Reino Unido", "GBP", "en", false),
        Country("CH", "Suiza", "CHF", "de", false),
        Country("US", "EE. UU.", "USD", "en", false),
    )

    private val byCode = all.associateBy { it.code }

    fun get(code: String?): Country? = if (code == null) null else byCode[code]
    fun name(code: String?): String = get(code)?.name ?: code ?: ""
    fun isEu(code: String?): Boolean = get(code)?.eu == true

    private val tldToCountry = mapOf(
        "de" to "DE", "es" to "ES", "fr" to "FR", "it" to "IT", "nl" to "NL", "be" to "BE", "at" to "AT",
        "pt" to "PT", "ie" to "IE", "pl" to "PL", "se" to "SE", "dk" to "DK", "cz" to "CZ", "uk" to "GB",
        "ch" to "CH", "us" to "US",
    )

    private val googleHost = Regex("(^|\\.)google\\.")

    /** País de una tienda según el dominio (.co.uk -> GB). Null si el dominio no lo dice. */
    fun fromUrl(url: String): String? {
        val host = try {
            URI(url).host?.lowercase()
        } catch (e: Exception) {
            null
        } ?: return null
        if (googleHost.containsMatchIn(host)) return null
        return tldToCountry[host.substringAfterLast('.')]
    }
}

object EbayMarkets {
    val countryOf: Map<String, String> = mapOf(
        "EBAY_DE" to "DE", "EBAY_ES" to "ES", "EBAY_FR" to "FR", "EBAY_IT" to "IT", "EBAY_NL" to "NL",
        "EBAY_AT" to "AT", "EBAY_BE" to "BE", "EBAY_IE" to "IE", "EBAY_PL" to "PL", "EBAY_GB" to "GB",
        "EBAY_CH" to "CH", "EBAY_US" to "US",
    )
}
