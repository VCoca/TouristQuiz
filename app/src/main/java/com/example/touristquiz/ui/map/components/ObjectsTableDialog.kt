package com.example.touristquiz.ui.map.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.touristquiz.data.ObjectRepository

@Composable
fun ObjectsTableDialog(
    visible: Boolean,
    objects: List<ObjectRepository.TouristObject>,
    onDismiss: () -> Unit,
    onOpenObject: (ObjectRepository.TouristObject) -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("Zatvori") } },
        title = { Text("Svi objekti") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)) {
                    Text("Ime", modifier = Modifier.weight(2f).padding(end = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    Text("Vlasnik", modifier = Modifier.weight(1.5f).padding(end = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    Text("Lat", modifier = Modifier.weight(1f).padding(end = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    Text("Lng", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                }
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(objects, key = { it.id }) { obj ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenObject(obj) }
                                .padding(vertical = 6.dp)
                        ) {
                            Text(obj.name, modifier = Modifier.weight(2f).padding(end = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                            Text(obj.ownerName ?: obj.ownerUid, modifier = Modifier.weight(1.5f).padding(end = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                            Text("%.5f".format(obj.latLng.latitude), modifier = Modifier.weight(1f).padding(end = 4.dp), maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
                            Text("%.5f".format(obj.latLng.longitude), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
                        }
                    }
                }
            }
        }
    )
}
