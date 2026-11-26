package com.bratneytg.ganga.work

import android.content.Context
import com.bratneytg.ganga.data.Meta
import com.bratneytg.ganga.data.Store
import com.bratneytg.ganga.net.Fx
import com.bratneytg.ganga.providers.Providers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex

data class Progress(val done: Int, val total: Int, val current: String?)

/**
 * Ejecuta las búsquedas (desde el botón, al abrir la app o desde el horario) de una en una:
 * si ya hay una en marcha, la segunda no empieza.
 */
class SearchEngine(private val context: Context, private val store: Store, private val fx: Fx) {
    private val mutex = Mutex()
    private val progressState = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = progressState

    val running: Boolean get() = mutex.isLocked

    /** Busca todos los artículos activos. Devuelve false si ya había otra búsqueda en marcha. */
    suspend fun runAll(trigger: String): Boolean {
        if (!mutex.tryLock()) return false
        try {
            fx.refresh()
            val ids = store.data.value.items.filter { it.enabled }.map { it.id }
            progressState.value = Progress(0, ids.size, null)
            ids.forEachIndexed { i, id ->
                progressState.value = Progress(i, ids.size, store.data.value.items.firstOrNull { it.id == id }?.name)
                checkOne(id)
                delay(400)
            }
            store.update { it.copy(meta = Meta(System.currentTimeMillis(), trigger)) }
            return true
        } finally {
            progressState.value = null
            mutex.unlock()
        }
    }

    suspend fun runOne(id: String): Boolean {
        if (!mutex.tryLock()) return false
        try {
            fx.refresh()
            progressState.value = Progress(0, 1, store.data.value.items.firstOrNull { it.id == id }?.name)
            checkOne(id)
            return true
        } finally {
            progressState.value = null
            mutex.unlock()
        }
    }

    private suspend fun checkOne(id: String) {
        val data = store.data.value
        val item = data.items.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        val result = try {
            Checker.check(item, data.settings, fx, Providers.all, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CheckResult(item.copy(lastCheckedAt = now, lastError = "Error inesperado: ${e.message}".take(300)), null)
        }
        val r = result.item
        // Solo se copian los campos calculados: si mientras tanto editaste el artículo, no se pisa.
        store.update { d ->
            d.copy(
                items = d.items.map {
                    if (it.id != id) it else it.copy(
                        offers = r.offers,
                        best = r.best,
                        medianLanded = r.medianLanded,
                        history = r.history,
                        lastNotifiedLanded = r.lastNotifiedLanded,
                        lastCheckedAt = r.lastCheckedAt,
                        lastError = r.lastError,
                    )
                },
            )
        }
        result.alert?.let { DealNotifier.notifyDeal(context, r, it.best, it.reasons) }
    }
}
