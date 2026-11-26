package com.bratneytg.ganga.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bratneytg.ganga.GangaApp
import com.bratneytg.ganga.core.Item
import com.bratneytg.ganga.core.Settings
import com.bratneytg.ganga.data.AppData
import com.bratneytg.ganga.work.Progress
import com.bratneytg.ganga.work.Scheduling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    data class Edit(val itemId: String?, val prefill: Prefill?) : Screen
    data object Prefs : Screen
}

/** Datos con los que se abre el editor cuando llega un enlace o texto compartido desde otra app. */
data class Prefill(val name: String = "", val target: String = "", val url: String = "")

/** Lo que el usuario escribe en el editor, tal cual (texto). */
data class ItemInput(
    val name: String,
    val query: String,
    val target: String,
    val minPrice: String,
    val condition: String,
    val mustInclude: String,
    val exclude: String,
    val filterAccessories: Boolean,
    val urls: String,
    val countries: Set<String>,
    val notes: String,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ga = app as GangaApp

    val data: StateFlow<AppData> = ga.store.data
    val progress: StateFlow<Progress?> = ga.engine.progress

    var screen by mutableStateOf<Screen>(Screen.Home)
    var message by mutableStateOf<String?>(null)

    init {
        refreshIfStale()
    }

    /** Al abrir la app, si la última búsqueda tiene más de 6 horas, busca de nuevo. */
    private fun refreshIfStale() {
        viewModelScope.launch(Dispatchers.Default) {
            val d = data.value
            val last = d.meta.lastRunAt
            val stale = last == null || System.currentTimeMillis() - last > 6L * 3600 * 1000
            if (stale && d.items.any { it.enabled }) ga.engine.runAll("open")
        }
    }

    fun runAll() {
        viewModelScope.launch(Dispatchers.Default) {
            if (!ga.engine.runAll("manual")) message = "Ya hay una búsqueda en marcha"
        }
    }

    fun runOne(id: String) {
        viewModelScope.launch(Dispatchers.Default) {
            if (!ga.engine.runOne(id)) message = "Ya hay una búsqueda en marcha"
        }
    }

    fun delete(id: String) {
        ga.store.update { d -> d.copy(items = d.items.filter { it.id != id }) }
        message = "Artículo eliminado"
    }

    fun saveSettings(settings: Settings) {
        ga.store.update { it.copy(settings = settings) }
        Scheduling.schedule(getApplication<Application>(), settings, replace = true)
        message = "Ajustes guardados"
    }

    fun openEditor(itemId: String?, prefill: Prefill? = null) {
        screen = Screen.Edit(itemId, prefill)
    }

    /** Llega un enlace o texto compartido desde el navegador o una app de tienda. */
    fun handleShare(text: String?, subject: String?) {
        if (text.isNullOrBlank()) return
        val url = Regex("https?://\\S+").find(text)?.value
        val rest = text.replace(url ?: "", "").trim()
        val name = (subject?.takeIf { it.isNotBlank() } ?: rest).take(120)
        screen = Screen.Edit(null, Prefill(name = name, url = url ?: ""))
    }

    private fun parseMoney(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    private fun words(text: String): List<String> =
        text.split(',').map { it.trim().take(40) }.filter { it.isNotEmpty() }.take(20)

    /** Guarda un artículo nuevo (id = null) o los cambios de uno existente. Devuelve un error o null. */
    fun saveItem(id: String?, input: ItemInput): String? {
        val name = input.name.trim().take(120)
        if (name.isEmpty()) return "Ponle un nombre al artículo"

        val target: Double? = if (input.target.isBlank()) null else (parseMoney(input.target)?.takeIf { it > 0 } ?: return "El precio objetivo no es válido")
        val minPrice: Double? = if (input.minPrice.isBlank()) null else (parseMoney(input.minPrice)?.takeIf { it > 0 } ?: return "El precio mínimo no es válido")

        val urlRegex = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
        val urls = input.urls.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(10)
        urls.firstOrNull { !urlRegex.matches(it) }?.let { return "La dirección «${it.take(40)}» no es válida: debe empezar por http:// o https://" }

        val query = input.query.trim().take(160).ifEmpty { name }
        val countries = input.countries.toList()
        val existing = id?.let { wanted -> data.value.items.firstOrNull { it.id == wanted } }

        val base = existing ?: Item(id = UUID.randomUUID().toString(), name = name, query = query, createdAt = System.currentTimeMillis())
        val updated = base.copy(
            name = name,
            query = query,
            condition = input.condition,
            mustInclude = words(input.mustInclude),
            exclude = words(input.exclude),
            filterAccessories = input.filterAccessories,
            targetPrice = target,
            minPrice = minPrice,
            countries = countries,
            urls = urls,
            notes = input.notes.trim().take(300),
            lastNotifiedLanded = if (existing != null && existing.targetPrice == target) existing.lastNotifiedLanded else null,
        )
        val searchChanged = existing == null ||
            existing.query != updated.query || existing.condition != updated.condition ||
            existing.urls != updated.urls || existing.countries != updated.countries ||
            existing.mustInclude != updated.mustInclude || existing.exclude != updated.exclude ||
            existing.filterAccessories != updated.filterAccessories || existing.minPrice != updated.minPrice

        ga.store.update { d ->
            if (existing == null) d.copy(items = d.items + updated)
            else d.copy(items = d.items.map { if (it.id == updated.id) updated else it })
        }
        screen = Screen.Home
        message = if (existing == null) "Listo. Ganga ya está buscando las mejores ofertas." else "Cambios guardados"
        if (searchChanged) runOne(updated.id)
        return null
    }
}
