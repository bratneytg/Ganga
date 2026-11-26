package com.bratneytg.ganga.core

import java.text.Normalizer

/**
 * Decide si el título de una oferta corresponde de verdad a lo que buscas
 * (y no a una funda, un cable o un modelo parecido).
 */
object Match {
    private val stopwords = setOf(
        "de", "del", "la", "el", "los", "las", "con", "para", "the", "and", "for", "with", "und", "mit",
        "fur", "pour", "per", "von", "en", "y", "a",
    )

    // Si NO las has pedido en tu búsqueda, un título con estas palabras es un accesorio.
    private val accessoryWords = setOf(
        "funda", "fundas", "carcasa", "carcasas", "protector", "case", "cases", "cover", "hulle", "hullen",
        "schutzfolie", "panzerglas", "coque", "etui", "custodia", "skin", "sticker", "cable", "kabel", "cavo",
        "ersatz", "ersatzteil", "recambio", "repuesto", "replacement", "adaptador", "adapter", "soporte",
        "halterung", "bolsa", "tasche", "mando",
    )

    private val usedRegex = Regex(
        "\\b(gebraucht|used|usado|usada|segunda mano|d'occasion|occasion|usato|tweedehands|refurbished|" +
            "reacondicionado|generalüberholt|generalueberholt|renewed|b-ware|open box|ricondizionato|" +
            "reconditionné|reconditionne|aufgearbeitet)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val accents = Regex("\\p{M}+")
    private val nonAlnum = Regex("[^a-z0-9]+")
    private val edgeJunk = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")
    private val digit = Regex("\\d")

    private fun strip(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(accents, "")

    fun compact(s: String): String = strip(s).replace(Regex("[^a-z0-9]"), "")

    private fun words(s: String): List<String> = strip(s).split(nonAlnum).filter { it.isNotEmpty() }

    private class TitleIndex(val set: Set<String>, val compactTitle: String)

    private fun titleIndex(title: String): TitleIndex {
        val toks = words(title)
        val set = toks.toMutableSet()
        for (i in 0 until toks.size - 1) set.add(toks[i] + toks[i + 1]) // "128" + "gb" -> "128gb"
        return TitleIndex(set, compact(title))
    }

    private fun tokenPresent(token: String, idx: TitleIndex): Boolean {
        val c = compact(token)
        if (c.isEmpty()) return true
        if (c in idx.set) return true
        // Códigos de modelo largos (WH1000XM5) también se aceptan dentro de otra palabra.
        return c.length >= 5 && digit.containsMatchIn(c) && idx.compactTitle.contains(c)
    }

    private fun queryTokens(query: String): List<String> =
        query.split(Regex("\\s+"))
            .map { it.replace(edgeJunk, "") }
            .filter { it.isNotEmpty() && strip(it) !in stopwords }

    private fun wordPresent(w: String, idx: TitleIndex): Boolean =
        w in idx.set || (w.length >= 4 && idx.compactTitle.contains(w))

    fun isRelevant(title: String, item: Item): Boolean {
        if (title.isBlank()) return false
        val idx = titleIndex(title)
        val tokens = queryTokens(item.query)
        if (tokens.isEmpty()) return true

        // Todo lo que lleva números (modelos, capacidades) es obligatorio; del resto basta con ~65 %.
        val numeric = tokens.filter { digit.containsMatchIn(it) }
        val plain = tokens.filter { !digit.containsMatchIn(it) }
        if (!numeric.all { tokenPresent(it, idx) }) return false
        if (plain.isNotEmpty()) {
            val hits = plain.count { tokenPresent(it, idx) }
            if (hits.toDouble() / plain.size < 0.65) return false
        }

        for (must in item.mustInclude) {
            if (!words(must).all { wordPresent(it, idx) }) return false
        }
        for (bad in item.exclude) {
            val parts = words(bad)
            if (parts.isNotEmpty() && parts.all { wordPresent(it, idx) }) return false
        }

        if (item.filterAccessories) {
            val asked = words(item.query).toSet()
            if (words(title).any { it in accessoryWords && it !in asked }) return false
        }
        return true
    }

    fun detectCondition(title: String): String = if (usedRegex.containsMatchIn(title)) "used" else "new"
}
