// ─── ui/pagos/EncabezadoLista.kt ─────────────────────────────────────────────
// Encabezado común de las listas (Inquilinos, Cuartos, Cuartos Libres) con el
// total de registros en una insignia junto al título.
package com.example.myapplication.ui.pagos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Fila de chips para filtrar una lista por piso. Devuelve el id del piso elegido
 * (null = todos). Se oculta sola cuando hay un único piso: filtrar entre una
 * opción no aporta nada y roba altura a la lista.
 *
 * La usan Inquilinos y Cuartos Libres, que solo se diferencian en de dónde salen
 * los pisos.
 */
@Composable
fun FiltroPisos(
    pisos: List<PisoFiltro>,
    seleccionado: String?,
    onSeleccionar: (String?) -> Unit
) {
    if (pisos.size <= 1) return

    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val colores = @Composable {
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = AppTheme.colores.doradoContenedor,
                selectedLabelColor     = AppTheme.colores.doradoContenedorTexto
            )
        }
        FilterChip(
            selected = seleccionado == null,
            onClick  = { onSeleccionar(null) },
            label    = { Text("Todos") },
            colors   = colores()
        )
        pisos.forEach { p ->
            FilterChip(
                selected = seleccionado == p.idPiso,
                // Volver a tocar el piso activo lo deselecciona: un toque menos que
                // ir hasta "Todos".
                onClick  = { onSeleccionar(if (seleccionado == p.idPiso) null else p.idPiso) },
                label    = { Text(p.etiqueta) },
                colors   = colores()
            )
        }
    }
}
