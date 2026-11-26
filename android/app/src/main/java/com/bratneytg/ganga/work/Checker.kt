package com.bratneytg.ganga.work

import com.bratneytg.ganga.core.Engine
import com.bratneytg.ganga.core.HistoryPoint
import com.bratneytg.ganga.core.Item
import com.bratneytg.ganga.core.Offer
import com.bratneytg.ganga.core.Price
import com.bratneytg.ganga.core.RawOffer
import com.bratneytg.ganga.core.Settings
import com.bratneytg.ganga.net.Fx
import com.bratneytg.ganga.providers.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

data class DealAlert(val best: Offer, val reasons: List<String>)
data class CheckResult(val item: Item, val alert: DealAlert?)

/** Busca un artículo en todas las fuentes activas y devuelve el artículo actualizado. */
object Checker {
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: TimeoutCancellationException) {
        Result.failure(e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun check(item: Item, settings: Settings, fx: Fx, providers: List<Provider>, now: Long): CheckResult {
        val updated = item.copy(lastCheckedAt = now)
        val active = providers.filter { it.isConfigured(settings) && (!it.needsUrls || item.urls.isNotEmpty()) }
        if (active.isEmpty()) {
            return CheckResult(
                updated.copy(lastError = "No hay ninguna fuente activa: añade una clave en Ajustes, activa el modo demo o pega la URL de una tienda."),
                null,
            )
        }

        val results = coroutineScope {
            active.map { p -> async { attempt { withTimeout(180_000L) { p.search(item, settings, fx) } } } }.awaitAll()
        }

        val raw = ArrayList<RawOffer>()
        val problems = ArrayList<String>()
        results.forEachIndexed { i, r ->
            val ok = r.getOrNull()
            if (ok != null) {
                raw.addAll(ok.offers)
                problems.addAll(ok.warnings)
            } else {
                val e = r.exceptionOrNull()
                problems.add("${active[i].name}: ${e?.message ?: e?.javaClass?.simpleName ?: "error"}")
            }
        }
        val error = problems.joinToString(" · ").take(600).ifEmpty { null }
        val withError = updated.copy(lastError = error)
        // Si todas las fuentes fallaron se conservan las ofertas anteriores.
        if (results.all { it.isFailure }) return CheckResult(withError, null)

        val offers = Engine.processOffers(raw, item, settings, fx)
        val best = Engine.best(offers)
        val good = offers.filter { !it.unavailable && !it.suspicious }
        val median = Engine.median(good.map { it.landedEur })?.let { Price.round2(it) }
        val prior = item.history
        val history = if (best != null) {
            (prior + HistoryPoint(now, best.landedEur, best.priceEur, best.store, best.country)).takeLast(Engine.MAX_HISTORY)
        } else {
            prior
        }

        var lastNotified = item.lastNotifiedLanded
        var alert: DealAlert? = null
        if (best != null) {
            val decision = Engine.decideNotification(item.targetPrice, lastNotified, best.landedEur, prior.map { it.landed })
            lastNotified = decision.lastNotified
            if (decision.reasons.isNotEmpty()) {
                lastNotified = best.landedEur
                alert = DealAlert(best, decision.reasons)
            }
        }

        return CheckResult(
            withError.copy(
                offers = offers,
                best = best,
                medianLanded = median,
                history = history,
                lastNotifiedLanded = lastNotified,
            ),
            alert,
        )
    }
}
