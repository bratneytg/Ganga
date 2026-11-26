package com.bratneytg.ganga.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bratneytg.ganga.core.Countries
import com.bratneytg.ganga.core.Fmt
import com.bratneytg.ganga.core.Item
import com.bratneytg.ganga.core.Offer
import com.bratneytg.ganga.core.Settings
import com.bratneytg.ganga.work.Progress
import com.bratneytg.ganga.work.Scheduling

private fun openUrl(handler: UriHandler, url: String) {
    runCatching { handler.openUri(url) }
}

@Composable
fun HomeScreen(vm: MainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var deleting by remember { mutableStateOf<Item?>(null) }
    val running = progress != null

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
    ) {
        item { Header(data.meta.lastRunAt, data.settings, progress, running, onRun = { vm.runAll() }, onSettings = { vm.screen = Screen.Prefs }) }
        item { SourceBanner(data.settings, data.items, onSettings = { vm.screen = Screen.Prefs }) }
        item {
            QuickAdd(
                onAdd = { name, target ->
                    val error = vm.saveItem(null, ItemInput(name, "", target, "", "new", "", "", true, "", emptySet(), ""))
                    if (error != null) vm.message = error
                    error == null
                },
                onMore = { name, target -> vm.openEditor(null, Prefill(name = name, target = target)) },
            )
        }
        if (data.items.isEmpty()) {
            item { EmptyState() }
        } else {
            items(data.items, key = { it.id }) { item ->
                ItemRow(
                    item = item,
                    settings = data.settings,
                    open = item.id in expanded,
                    running = running,
                    busyHere = progress?.current == item.name,
                    uriHandler = uriHandler,
                    onToggle = { expanded = if (item.id in expanded) expanded - item.id else expanded + item.id },
                    onCheck = { vm.runOne(item.id) },
                    onEdit = { vm.openEditor(item.id) },
                    onDelete = { deleting = item },
                )
            }
        }
    }

    val toDelete = deleting
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Dejar de vigilar «${toDelete.name}»?") },
            text = { Text("Se borrará también su historial de precios.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(toDelete.id)
                    deleting = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Header(
    lastRunAt: Long?,
    settings: Settings,
    progress: Progress?,
    running: Boolean,
    onRun: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(8.dp))
            Text("Ganga", fontFamily = Fonts.condensedBold, fontSize = 30.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onRun, enabled = !running) { Text("Buscar ahora") }
            TextButton(onClick = onSettings) { Text("Ajustes") }
        }
        val status = if (progress != null) {
            "Buscando" + (progress.current?.let { ": $it" } ?: "") + " (${minOf(progress.done + 1, progress.total)} de ${progress.total})…"
        } else {
            val parts = ArrayList<String>()
            if (lastRunAt != null) parts.add("Última búsqueda ${Fmt.whenText(lastRunAt)}.")
            Scheduling.nextRunMillis(settings)?.let { parts.add("Próxima ${Fmt.whenText(it)}.") }
            parts.joinToString(" ")
        }
        if (status.isNotEmpty()) {
            Text(status, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SourceBanner(settings: Settings, items: List<Item>, onSettings: () -> Unit) {
    val hasKey = settings.serperApiKey.isNotBlank() || (settings.ebayClientId.isNotBlank() && settings.ebayClientSecret.isNotBlank())
    val hasUrls = items.any { it.urls.isNotEmpty() }
    if (!hasKey && !hasUrls && !settings.demoMode) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .background(Palette.warnWash())
                .padding(12.dp),
        ) {
            Text(
                "Ganga todavía no tiene de dónde sacar precios. Añade la clave gratuita de Serper (Google Shopping) para buscar en cientos de tiendas, o activa el modo demo para probar la app.",
                color = Palette.warn(),
                fontSize = 14.sp,
            )
            TextButton(onClick = onSettings) { Text("Abrir ajustes") }
        }
    } else if (settings.demoMode) {
        Text(
            "Modo demo: las ofertas son inventadas. Desactívalo en Ajustes cuando añadas una clave real.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun QuickAdd(onAdd: (String, String) -> Boolean, onMore: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("¿Qué quieres comprar?") },
            placeholder = { Text("Sony WH-1000XM5") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Objetivo") },
                suffix = { Text("€") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (onAdd(name, target)) {
                        name = ""
                        target = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.tag, contentColor = Palette.ink),
            ) { Text("Vigilar", fontWeight = FontWeight.SemiBold) }
        }
        TextButton(onClick = { onMore(name, target) }) { Text("Más opciones") }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface, thickness = 2.dp)
    }
}

@Composable
private fun EmptyState() {
    Column(modifier = Modifier.padding(vertical = 32.dp)) {
        Text("Aún no vigilas nada", fontFamily = Fonts.condensedBold, fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Escribe arriba qué quieres comprar y, si quieres, el precio al que te parecería una ganga. Ganga lo buscará en varias tiendas y países varias veces al día y te avisará cuando baje.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemRow(
    item: Item,
    settings: Settings,
    open: Boolean,
    running: Boolean,
    busyHere: Boolean,
    uriHandler: UriHandler,
    onToggle: () -> Unit,
    onCheck: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val best = item.best
    val target = item.targetPrice
    val deal = best != null && target != null && best.landedEur <= target
    val gray = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                item.name,
                fontFamily = Fonts.condensedBold,
                fontSize = 22.sp,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            if (best != null) PriceTag(best.landedEur, deal)
            else Text("Sin precio", fontFamily = Fonts.condensedBold, fontSize = 20.sp, color = gray)
        }
        Spacer(Modifier.height(6.dp))

        if (best != null) {
            val ship = if (best.shippingEur == 0.0 && !best.shippingEstimated) "envío gratis" else (if (best.shippingEstimated) "~" else "") + Fmt.eur(best.shippingEur) + " de envío"
            val importText = if (best.importEur > 0) ", ~${Fmt.eur(best.importEur)} de importación" else ""
            Text("Mejor oferta en ${best.store} (${Countries.name(best.country)}). ${Fmt.eur(best.priceEur)} y $ship$importText.")
        } else {
            Text(
                if (item.lastCheckedAt != null) "No se ha encontrado ninguna oferta que coincida." else "Pendiente de la primera búsqueda.",
                color = gray,
            )
        }

        val flags = ArrayList<@Composable () -> Unit>()
        if (deal) flags.add { Flag("Por debajo de tu objetivo", highlight = true) }
        if (best != null) {
            val prior = item.history.dropLast(1).map { it.landed }
            if (prior.size >= 3 && best.landedEur < prior.min()) flags.add { Flag("Mínimo histórico") }
            val med = item.medianLanded
            if (med != null && best.landedEur < med * 0.95) flags.add { Flag("${Math.round((1 - best.landedEur / med) * 100)} % bajo la mediana") }
            if (best.foreign) flags.add { Flag("Tienda de ${Countries.name(best.country)}") }
        }
        if (flags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { flags.forEach { it() } }
        }

        val meta = ArrayList<String>()
        if (target != null) {
            meta.add(
                if (best == null) "Objetivo ${Fmt.eur(target)}."
                else if (deal) "Objetivo ${Fmt.eur(target)}: lo superas por ${Fmt.eur(target - best.landedEur)}."
                else "Objetivo ${Fmt.eur(target)}: te faltan ${Fmt.eur(best.landedEur - target)}.",
            )
        }
        if (item.offers.isNotEmpty()) meta.add("${item.offers.size} ofertas encontradas.")
        item.lastCheckedAt?.let { meta.add("Revisado ${Fmt.whenText(it)}.") }
        if (busyHere) meta.add("Buscando ahora…")
        if (meta.isNotEmpty()) {
            Text(meta.joinToString(" "), fontSize = 13.sp, color = gray, modifier = Modifier.padding(top = 6.dp))
        }
        if (item.lastError != null) {
            Text(item.lastError, fontSize = 13.sp, color = Palette.warn(), modifier = Modifier.padding(top = 6.dp))
        }

        if (item.history.size >= 2) {
            Spark(item.history, target, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }

        FlowRow(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            if (item.offers.isNotEmpty()) TextButton(onClick = onToggle) { Text(if (open) "Ocultar ofertas" else "Ver ofertas") }
            TextButton(onClick = onCheck, enabled = !running) { Text("Buscar ahora") }
            TextButton(onClick = onEdit) { Text("Editar") }
            TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
        }

        if (open && item.offers.isNotEmpty()) {
            OffersList(item.offers, settings, uriHandler)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun OffersList(offers: List<Offer>, settings: Settings, uriHandler: UriHandler) {
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Text(
            "${offers.size} ofertas por precio total hasta ${Countries.name(settings.homeCountry)}. El signo ~ marca estimaciones.",
            fontSize = 12.sp,
            color = gray,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        offers.forEachIndexed { index, o ->
            val dim = o.suspicious || o.unavailable
            val note = when {
                o.unavailable -> "Agotado"
                o.suspicious -> "Precio sospechosamente bajo"
                else -> null
            }
            val bestRow = index == 0 && !dim
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (bestRow) Modifier.background(Palette.tag.copy(alpha = 0.22f)) else Modifier)
                    .clickable { openUrl(uriHandler, o.url) }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(o.store, fontWeight = FontWeight.SemiBold, color = if (dim) gray else MaterialTheme.colorScheme.onSurface)
                    Text(
                        Countries.name(o.country) + (if (o.foreign) ", fuera de tu país" else "") + (if (note != null) ". $note" else ""),
                        fontSize = 12.sp,
                        color = gray,
                    )
                    Text(o.title, fontSize = 12.sp, color = gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                    Text(Fmt.eur(o.landedEur), fontWeight = FontWeight.Bold, color = if (dim) gray else MaterialTheme.colorScheme.onSurface)
                    val ship = if (o.shippingEur == 0.0 && !o.shippingEstimated) "gratis" else (if (o.shippingEstimated) "~" else "") + Fmt.eur(o.shippingEur)
                    var detail = "${Fmt.eur(o.priceEur)} + envío $ship"
                    if (o.importEur > 0) detail += " + ~${Fmt.eur(o.importEur)} imp."
                    Text(detail, fontSize = 11.sp, color = gray)
                    if (o.currency != "EUR") Text("${Fmt.num(o.price)} ${o.currency}", fontSize = 11.sp, color = gray)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}
