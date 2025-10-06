package com.example.touristquiz.ui.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement

@Composable
fun FiltersDialog(
    visible: Boolean,
    onlyMine: Boolean,
    onOnlyMineChange: (Boolean) -> Unit,
    creatorFilter: String,
    onCreatorFilterChange: (String) -> Unit,
    typeEnabled: Map<String, Boolean>,
    onTypeEnabledChange: (String, Boolean) -> Unit,
    limitByDistance: Boolean,
    onLimitByDistanceChange: (Boolean) -> Unit,
    maxDistanceMeters: Float,
    onMaxDistanceMetersChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    // Localized labels mapped to canonical keys used by MapScreen filters
    val typeOptions = listOf(
        "Turistička atrakcija" to "attraction",
        "Kulturni objekat" to "cultural",
        "Istorijska lokacija" to "historical"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("Sacuvaj") } },
        title = { Text("Filteri") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = onlyMine, onCheckedChange = onOnlyMineChange)
                    Text("Samo moji objekti")
                }
                OutlinedTextField(value = creatorFilter, onValueChange = onCreatorFilterChange, label = { Text("Vlasnik") })
                Text("Tipovi")
                typeOptions.forEach { (label, key) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = typeEnabled[key] == true,
                            onCheckedChange = { v -> onTypeEnabledChange(key, v) }
                        )
                        Text(label)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = limitByDistance, onCheckedChange = onLimitByDistanceChange)
                    Text("Limitiraj po udaljenosti")
                }
                if (limitByDistance) {
                    Text("Max udaljenost: ${maxDistanceMeters.toInt()} m")
                    Slider(value = maxDistanceMeters, onValueChange = onMaxDistanceMetersChange, valueRange = 100f..5000f)
                }
            }
        }
    )
}
