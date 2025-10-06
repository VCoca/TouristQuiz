package com.example.touristquiz.ui.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import com.example.touristquiz.ui.TimeFilter
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box

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
    selectedTimeFilter: TimeFilter,
    onSelectedTimeFilterChange: (TimeFilter) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

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

                Text("Vremenski filter")
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { expanded = true }) { Text(selectedTimeFilter.label) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        TimeFilter.values().forEach { tf ->
                            DropdownMenuItem(
                                text = { Text(tf.label) },
                                onClick = {
                                    onSelectedTimeFilterChange(tf)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}
