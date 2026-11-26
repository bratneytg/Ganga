package com.bratneytg.ganga.providers

import com.bratneytg.ganga.core.Item
import com.bratneytg.ganga.core.RawOffer
import com.bratneytg.ganga.core.Settings
import com.bratneytg.ganga.net.Fx

data class ProviderResult(val offers: List<RawOffer>, val warnings: List<String> = emptyList())

/** Una fuente de precios. Para añadir otra: implementa esta interfaz y regístrala en [Providers.all]. */
interface Provider {
    val id: String
    val name: String
    val needsUrls: Boolean get() = false

    fun isConfigured(settings: Settings): Boolean

    /** Devuelve las ofertas encontradas; lanza una excepción solo si la fuente falló por completo. */
    suspend fun search(item: Item, settings: Settings, fx: Fx): ProviderResult
}

object Providers {
    val all: List<Provider> by lazy { listOf(SerperProvider, EbayProvider, UrlWatchProvider, DemoProvider) }

    fun active(settings: Settings, item: Item): List<Provider> =
        all.filter { it.isConfigured(settings) && (!it.needsUrls || item.urls.isNotEmpty()) }
}
