package com.bratneytg.ganga.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bratneytg.ganga.core.Countries

private fun numberText(value: Double?): String =
    if (value == null) "" else if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditScreen(vm: MainViewModel, screen: Screen.Edit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val existing = screen.itemId?.let { wanted -> data.items.firstOrNull { it.id == wanted } }
    val prefill = screen.prefill

    var name by remember { mutableStateOf(existing?.name ?: prefill?.name ?: "") }
    var query by remember { mutableStateOf(existing?.let { if (it.query != it.name) it.query else "" } ?: "") }
    var target by remember { mutableStateOf(numberText(existing?.targetPrice).ifEmpty { prefill?.target ?: "" }) }
    var minPrice by remember { mutableStateOf(numberText(existing?.minPrice)) }
    var condition by remember { mutableStateOf(existing?.condition ?: "new") }
    var must by remember { mutableStateOf(existing?.mustInclude?.joinToString(", ") ?: "") }
    var exclude by remember { mutableStateOf(existing?.exclude?.joinToString(", ") ?: "") }
    var accessories by remember { mutableStateOf(existing?.filterAccessories ?: true) }
    var urls by remember { mutableStateOf(existing?.urls?.joinToString("\n") ?: prefill?.url ?: "") }
    var countries by remember { mutableStateOf(existing?.countries?.toSet() ?: emptySet()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.screen = Screen.Home }) { Text("Volver") }
        }
        Text(
            if (existing == null) "Nuevo artículo" else "Editar artículo",
            fontFamily = Fonts.condensedBold,
            fontSize = 28.sp,
        )
        Spacer(Modifier.height(12.dp))

        Field("Nombre", name, { name = it }, placeholder = "Sony WH-1000XM5")
        Field(
            "Texto de búsqueda (opcional)", query, { query = it },
            placeholder = "Si lo dejas vacío se usa el nombre",
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Precio objetivo", target, { target = it }, suffix = "€", decimal = true, modifier = Modifier.weight(1f))
            Field("Precio mínimo", minPrice, { minPrice = it }, suffix = "€", decimal = true, modifier = Modifier.weight(1f))
        }
        Hint("El mínimo descarta ofertas sospechosamente baratas (fundas, piezas sueltas, estafas).")

        Text("Estado", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("new" to "Nuevo", "used" to "Usado", "any" to "Da igual").forEach { (value, label) ->
                FilterChip(selected = condition == value, onClick = { condition = value }, label = { Text(label) })
            }
        }

        Spacer(Modifier.height(8.dp))
        Field("Debe incluir (separado por comas)", must, { must = it }, placeholder = "256GB, negro")
        Field("Excluir (separado por comas)", exclude, { exclude = it }, placeholder = "reacondicionado, funda")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Descartar accesorios (fundas, cables, carcasas…)", modifier = Modifier.weight(1f))
            Switch(checked = accessories, onCheckedChange = { accessories = it })
        }

        Spacer(Modifier.height(8.dp))
        Field(
            "Enlaces concretos a vigilar (uno por línea)", urls, { urls = it },
            placeholder = "https://tienda.com/producto", singleLine = false, minLines = 3,
        )
        Hint("Ganga lee el precio de esas páginas aunque no salgan en Google Shopping.")

        Text("Buscar en estos países", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Countries.all.forEach { c ->
                FilterChip(
                    selected = c.code in countries,
                    onClick = { countries = if (c.code in countries) countries - c.code else countries + c.code },
                    label = { Text(c.name) },
                )
            }
        }
        Hint(
            if (countries.isEmpty()) "Sin marcar ninguno: se usan los países de Ajustes."
            else "Solo se buscará en estos países para este artículo.",
        )

        Spacer(Modifier.height(8.dp))
        Field("Notas", notes, { notes = it }, singleLine = false, minLines = 2)

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    error = vm.saveItem(
                        existing?.id,
                        ItemInput(name, query, target, minPrice, condition, must, exclude, accessories, urls, countries, notes),
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.tag, contentColor = Palette.ink),
            ) { Text(if (existing == null) "Guardar y buscar" else "Guardar cambios", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { vm.screen = Screen.Home }) { Text("Cancelar") }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    suffix: String? = null,
    decimal: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = if (decimal) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}

@Composable
internal fun Hint(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
    )
}
