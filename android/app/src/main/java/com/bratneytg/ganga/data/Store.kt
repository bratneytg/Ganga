package com.bratneytg.ganga.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/**
 * Datos de la app en un único archivo JSON privado (filesDir/ganga.json), con escritura atómica.
 * Se observa como un StateFlow desde la interfaz.
 */
class Store(private val file: File) {
    private val state = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = state

    init {
        load()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            state.value = Codec.fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            // Archivo ilegible: se guarda una copia y se empieza de cero en vez de perderlo sin rastro.
            try {
                file.copyTo(File(file.parentFile, "ganga.json.corrupto-${System.currentTimeMillis()}"), overwrite = true)
            } catch (ignored: Exception) {
            }
        }
    }

    /** Aplica un cambio de forma atómica y lo guarda en disco. */
    @Synchronized
    fun update(block: (AppData) -> AppData) {
        val next = block(state.value)
        state.value = next
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Codec.toJson(next).toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
