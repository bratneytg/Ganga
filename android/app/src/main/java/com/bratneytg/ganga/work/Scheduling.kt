package com.bratneytg.ganga.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bratneytg.ganga.GangaApp
import com.bratneytg.ganga.core.Settings
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Tarea que Android lanza a cada hora programada: busca todos los artículos y avisa de las gangas. */
class SearchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GangaApp
        try {
            app.engine.runAll("scheduled")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Se ignora: la siguiente hora programada lo intentará de nuevo.
        }
        return Result.success()
    }
}

/**
 * Una tarea periódica de 24 h por cada hora del horario (por defecto 00:00, 06:00, 12:00 y 18:00).
 * Android decide el momento exacto para ahorrar batería, así que puede retrasarse un poco; si el
 * móvil estaba apagado o sin datos, la tarea se ejecuta en cuanto vuelve la conexión.
 */
object Scheduling {
    private const val TAG = "ganga-slot"

    /** Próxima vez (estrictamente futura) que se cumple alguna de las horas "HH:mm". */
    fun nextSlot(now: ZonedDateTime, times: List<String>): ZonedDateTime? {
        var best: ZonedDateTime? = null
        for (dayOffset in 0L..1L) {
            for (t in times) {
                val parts = t.split(":")
                val h = parts.getOrNull(0)?.toIntOrNull() ?: continue
                val m = parts.getOrNull(1)?.toIntOrNull() ?: continue
                val candidate = now.plusDays(dayOffset).withHour(h).withMinute(m).withSecond(0).withNano(0)
                val currentBest = best
                if (candidate.isAfter(now) && (currentBest == null || candidate.isBefore(currentBest))) best = candidate
            }
        }
        return best
    }

    /**
     * Programa las búsquedas. Con [replace] = true rehace todo el horario (al cambiar las horas
     * en Ajustes); con false respeta lo ya programado (al arrancar la app).
     */
    fun schedule(context: Context, settings: Settings, replace: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (replace) wm.cancelAllWorkByTag(TAG)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val now = ZonedDateTime.now()
        for (t in settings.scheduleTimes) {
            val next = nextSlot(now, listOf(t)) ?: continue
            val delayMs = Duration.between(now, next).toMillis()
            val request = PeriodicWorkRequestBuilder<SearchWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(TAG)
                .build()
            wm.enqueueUniquePeriodicWork(
                "$TAG-$t",
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    /** Próxima búsqueda programada de cualquiera de las horas, para mostrarla en pantalla. */
    fun nextRunMillis(settings: Settings): Long? =
        nextSlot(ZonedDateTime.now(), settings.scheduleTimes)?.toInstant()?.toEpochMilli()
}
