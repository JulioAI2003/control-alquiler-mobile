// ─── ui/pagos/EncabezadoLista.kt ─────────────────────────────────────────────
// Encabezado común de las listas (Inquilinos, Cuartos, Cuartos Libres) con el
// total de registros en una insignia junto al título.
package com.example.myapplication.ui.pagos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.AppTheme

/**
 * Título de una lista con el número de registros a su derecha.
 *
 * @param total registros cargados. `null` mientras la lista aún no se cargó (o
 *   si falló): en ese caso no se muestra insignia, para no cantar un "0" que
 *   parecería que no hay datos.
 * @param visibles registros que se están mostrando cuando hay un filtro activo.
 *   Si es distinto de [total], la insignia pasa a "3 de 12" para que el número
 *   no contradiga a la lista de abajo.
 */
@Composable
fun EncabezadoLista(titulo: String, total: Int?, visibles: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, fontWeight = FontWeight.Bold, color = AppTheme.colores.textoSuave)

        if (total != null) {
            val filtrando = visibles != null && visibles != total
            val etiqueta = if (filtrando) "$visibles de $total" else "$total"

            Spacer(Modifier.width(8.dp))
            Text(
                etiqueta,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colores.doradoContenedorTexto,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppTheme.colores.doradoContenedor)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    // Sin esto un lector de pantalla leería solo el número suelto.
                    .clearAndSetSemantics {
                        contentDescription =
                            if (filtrando) "$visibles de $total registros"
                            else "$total registros"
                    }
            )
        }
    }
}

/** Un piso al que se puede filtrar la lista de inquilinos. */
data class PisoFiltro(val idPiso: String, val etiqueta: String)

/** Opción del diálogo de retiro: un solo cuarto o todos los del inquilino. */
@Composable
fun OpcionRetiro(seleccionada: Boolean, titulo: String, detalle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = seleccionada, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(detalle, fontSize = 12.sp, color = AppTheme.colores.textoSuave)
        }
    }
}

/** Opción "sin filtro" del selector de pisos. */
private const val TODOS_LOS_PISOS = "Todos los pisos"

/**
 * Selector de piso para filtrar una lista. Devuelve el id del piso elegido
 * (null = todos). Se oculta solo cuando hay un único piso: filtrar entre una
 * opción no aporta nada y roba altura a la lista.
 *
 * Es un desplegable y no una fila de chips porque con varias casas los pisos no
 * entraban en el ancho y había que arrastrar en horizontal para ver los últimos.
 *
 * La usan Inquilinos y Cuartos Libres, que solo se diferencian en de dónde salen
 * los pisos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltroPisos(
    pisos: List<PisoFiltro>,
    seleccionado: String?,
    onSeleccionar: (String?) -> Unit
) {
    if (pisos.size <= 1) return

    var expandido by remember { mutableStateOf(false) }
    // Si el piso filtrado desaparece de la lista, el texto vuelve solo a "Todos".
    val etiquetaActual = pisos.firstOrNull { it.idPiso == seleccionado }?.etiqueta
        ?: TODOS_LOS_PISOS

    ExposedDropdownMenuBox(
        expanded = expandido,
        onExpandedChange = { expandido = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = etiquetaActual,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Piso") },
            leadingIcon = {
                Icon(Icons.Default.Layers, contentDescription = null, tint = AppTheme.colores.dorado)
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        TODOS_LOS_PISOS,
                        fontWeight = if (seleccionado == null) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = { onSeleccionar(null); expandido = false }
            )
            pisos.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Text(
                            p.etiqueta,
                            fontWeight = if (seleccionado == p.idPiso) FontWeight.Bold
                                         else FontWeight.Normal
                        )
                    },
                    onClick = { onSeleccionar(p.idPiso); expandido = false }
                )
            }
        }
    }
}
