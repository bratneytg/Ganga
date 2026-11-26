package com.bratneytg.ganga.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bratneytg.ganga.core.Countries
import com.bratneytg.ganga.core.EbayMarkets
import com.bratneytg.ganga.core.Settings
import com.bratneytg.ganga.core.ShippingRates
import com.bratneytg.ganga.work.DealNotifier

private fun money(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun parse(text: String, fallback: Double): Double =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 } ?: fallback

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrefsScreen(vm: MainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val current = data.settings
    val context = LocalContext.current

    var home by remember { mutableStateOf(current.homeCountry) }
    var zip by remember { mutableStateOf(current.homeZip) }
    var domestic by remember { mutableStateOf(money(current.shipping.domestic)) }
    var eu by remember { mutableStateOf(money(current.shipping.eu)) }
    var nonEu by remember { mutableStateOf(money(current.shipping.nonEu)) }
    var importPct by remember { mutableStateOf(money(current.shipping.importPct)) }
    var searchCountries by remember { mutableStateOf(current.searchCountries.toSet()) }
    var serper by remember { mutableStateOf(current.serperApiKey) }
    var ebayId by remember { mutableStateOf(current.ebayClientId) }
    var ebaySecret by remember { mutableStateOf(current.ebayClientSecret) }
    var markets by remember { mutableStateOf(current.ebayMarketplaces.toSet()) }
    var demo by remember { mutableStateOf(current.demoMode) }
    var times by remember { mutableStateOf(current.scheduleTimes) }
    var homeMenu by remember { mutableStateOf(false) }

    fun collect(): Settings = Settings(
        homeCountry = home,
        homeZip = zip.trim().take(12),
        searchCountries = Countries.all.map { it.code }.filter { it in searchCountries },
        scheduleTimes = times.distinct().sorted(),
        serperApiKey = serper.trim(),
        ebayClientId = ebayId.trim(),
        ebayClientSecret = ebaySecret.trim(),
        ebayMarketplaces = EbayMarkets.countryOf.keys.filter { it in markets },
        demoMode = demo,
        shipping = ShippingRates(
            domestic = parse(domestic, current.shipping.domestic),
            eu = parse(eu, current.shipping.eu),
            nonEu = parse(nonEu, current.shipping.nonEu),
            importPct = parse(importPct, current.shipping.importPct),
        ),
    )

    val queriesPerMonth = searchCountries.size * times.size * 30

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TextButton(onClick = { vm.screen = Screen.Home }) { Text("Volver") }
        Text("Ajustes", fontFamily = Fonts.condensedBold, fontSize = 28.sp)

        Section("Tu ubicación")
        Hint("Sirve para calcular el envío y la importación de cada oferta hasta tu casa.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { homeMenu = !homeMenu }) { Text("País: ${Countries.name(home)}") }
            Spacer(Modifier.width(8.dp))
            Field("Código postal", zip, { zip = it }, modifier = Modifier.weight(1f))
        }
        if (homeMenu) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Countries.all.forEach { c ->
                    FilterChip(selected = c.code == home, onClick = { home = c.code; homeMenu = false }, label = { Text(c.name) })
                }
            }
        }

        Section("Gastos de envío estimados")
        Hint("Se usan cuando la tienda no dice cuánto cuesta el envío.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("En tu país", domestic, { domestic = it }, suffix = "€", decimal = true, modifier = Modifier.weight(1f))
            Field("Dentro de la UE", eu, { eu = it }, suffix = "€", decimal = true, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Fuera de la UE", nonEu, { nonEu = it }, suffix = "€", decimal = true, modifier = Modifier.weight(1f))
            Field("IVA de importación", importPct, { importPct = it }, suffix = "%", decimal = true, modifier = Modifier.weight(1f))
        }

        Section("Dónde buscar")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Countries.all.forEach { c ->
                FilterChip(
                    selected = c.code in searchCountries,
                    onClick = { searchCountries = if (c.code in searchCountries) searchCountries - c.code else searchCountries + c.code },
                    label = { Text(c.name) },
                )
            }
        }
        Hint(
            "Con Serper se gasta una consulta por artículo, país y búsqueda. Con ${searchCountries.size} países y ${times.size} búsquedas al día son unas $queriesPerMonth consultas al mes por cada artículo.",
        )

        Section("Serper (Google Shopping)")
        Hint("Clave gratuita en serper.dev. Es la fuente principal: cientos de tiendas por país.")
        SecretField("Clave de Serper", serper, { serper = it })

        Section("eBay (opcional)")
        Hint("Claves de developer.ebay.com. Útil para productos nuevos y de segunda mano.")
        SecretField("Client ID", ebayId, { ebayId = it })
        SecretField("Client secret", ebaySecret, { ebaySecret = it })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EbayMarkets.countryOf.forEach { (market, country) ->
                FilterChip(
                    selected = market in markets,
                    onClick = { markets = if (market in markets) markets - market else markets + market },
                    label = { Text("eBay ${Countries.name(country)}") },
                )
            }
        }

        Section("Horario de búsquedas")
        Hint("Android decide la hora exacta para ahorrar batería: puede retrasarlas unos minutos.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            times.forEachIndexed { index, time ->
                OutlinedButton(onClick = {
                    val (h, m) = time.split(":").map { it.toIntOrNull() ?: 0 }
                    TimePickerDialog(context, { _, hour, minute ->
                        val picked = "%02d:%02d".format(hour, minute)
                        times = times.toMutableList().also { it[index] = picked }
                    }, h, m, true).show()
                }) { Text(time) }
            }
            if (times.size < 8) {
                TextButton(onClick = { times = times + "09:00" }) { Text("Añadir hora") }
            }
            if (times.size > 1) {
                TextButton(onClick = { times = times.dropLast(1) }) { Text("Quitar última") }
            }
        }

        Section("Modo demo")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Usar ofertas inventadas para probar la app", modifier = Modifier.weight(1f))
            Switch(checked = demo, onCheckedChange = { demo = it })
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { vm.saveSettings(collect()); vm.screen = Screen.Home },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.tag, contentColor = Palette.ink),
            ) { Text("Guardar ajustes", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { DealNotifier.notifyTest(context) }) { Text("Probar aviso") }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        fontFamily = Fonts.condensedBold,
        fontSize = 20.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
    )
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
        )
        if (value.isNotEmpty()) TextButton(onClick = { onChange("") }) { Text("Quitar") }
    }
}
