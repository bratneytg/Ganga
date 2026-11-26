package com.bratneytg.ganga.core

data class ParsedPrice(val value: Double, val currency: String?)

/** Interpreta precios escritos de cualquier forma: "1.299,00 €", "€1,299.00", "99 zł", "19,99". */
object Price {
    private val currencyPatterns: List<Pair<String, Regex>> = listOf(
        "CHF" to Regex("\\bCHF\\b|\\bFr\\.(?=\\s|\\d)", RegexOption.IGNORE_CASE),
        "EUR" to Regex("€|\\bEUR\\b", RegexOption.IGNORE_CASE),
        "GBP" to Regex("£|\\bGBP\\b", RegexOption.IGNORE_CASE),
        "PLN" to Regex("\\bPLN\\b|zł", RegexOption.IGNORE_CASE),
        "SEK" to Regex("\\bSEK\\b", RegexOption.IGNORE_CASE),
        "DKK" to Regex("\\bDKK\\b", RegexOption.IGNORE_CASE),
        "NOK" to Regex("\\bNOK\\b", RegexOption.IGNORE_CASE),
        "CZK" to Regex("\\bCZK\\b|Kč", RegexOption.IGNORE_CASE),
        "HUF" to Regex("\\bHUF\\b|\\bFt\\b", RegexOption.IGNORE_CASE),
        "RON" to Regex("\\bRON\\b|\\blei\\b", RegexOption.IGNORE_CASE),
        "USD" to Regex("US\\$|\\bUSD\\b|\\$"),
    )

    private val numberRegex = Regex(
        "\\d{1,3}(?:[ \\u00a0\\u202f.,]\\d{3})+(?:[.,]\\d{1,2})?(?!\\d)|\\d+(?:[.,]\\d{1,2})?",
    )

    fun round2(n: Double): Double = Math.round(n * 100.0) / 100.0

    private fun normalizeNumber(raw: String): Double? {
        var s = raw.replace(" ", "").replace(" ", "").replace(" ", "")
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        if (lastDot > -1 && lastComma > -1) {
            val decimal = if (lastDot > lastComma) '.' else ','
            val thousands = if (decimal == '.') ',' else '.'
            s = s.replace(thousands.toString(), "").replace(decimal, '.')
        } else if (lastDot > -1 || lastComma > -1) {
            val sep = if (lastDot > -1) '.' else ','
            val parts = s.split(sep)
            val isThousands = parts.size > 2 || (parts[1].length == 3 && parts[0] != "0")
            s = if (isThousands) parts.joinToString("") else parts.joinToString(".")
        }
        return s.toDoubleOrNull()
    }

    fun parse(text: String?, fallbackCurrency: String? = null): ParsedPrice? {
        if (text == null) return null
        val match = numberRegex.find(text) ?: return null
        val value = normalizeNumber(match.value) ?: return null
        if (value < 0 || value.isNaN() || value.isInfinite()) return null
        val currency = currencyPatterns.firstOrNull { it.second.containsMatchIn(text) }?.first
        return ParsedPrice(round2(value), currency ?: fallbackCurrency)
    }

    fun fromNumber(n: Double, fallbackCurrency: String? = null): ParsedPrice? {
        if (n.isNaN() || n.isInfinite() || n < 0) return null
        return ParsedPrice(round2(n), fallbackCurrency)
    }

    /** Acepta lo que venga de un JSON: número o texto. */
    fun fromAny(any: Any?, fallbackCurrency: String? = null): ParsedPrice? = when (any) {
        null -> null
        is Number -> fromNumber(any.toDouble(), fallbackCurrency)
        else -> parse(any.toString(), fallbackCurrency)
    }
}
