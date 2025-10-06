package com.example.touristquiz.ui.map.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.touristquiz.ui.map.model.QItem

@Composable
fun AddObjectDialog(
    visible: Boolean,
    newName: String,
    onNewNameChange: (String) -> Unit,
    newDetails: String,
    onNewDetailsChange: (String) -> Unit,
    newImageUri: Uri?,
    onPickImage: (Uri?) -> Unit,
    questions: List<QItem>,
    onChangeQuestionText: (index: Int, text: String) -> Unit,
    onChangeQuestionOption: (index: Int, optionIndex: Int, value: String) -> Unit,
    onChangeQuestionCorrectIndex: (index: Int, correctIndex: Int) -> Unit,
    onAddQuestion: () -> Unit,
    onRemoveQuestion: (index: Int) -> Unit,
    selectedType: String,
    onSelectedTypeChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val addImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onPickImage(uri)
    }

    val hasCompleteQuestion = questions.any { q ->
        q.text.isNotBlank() && q.options.size >= 3 && q.options.all { it.isNotBlank() }
    }
    val canCreate = newName.isNotBlank() && newDetails.isNotBlank() && hasCompleteQuestion

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onCreate, enabled = canCreate) { Text("Napravi") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Otkaži") } },
        title = { Text("Novi objekat") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = newName, onValueChange = onNewNameChange, label = { Text("Ime") })
                OutlinedTextField(value = newDetails, onValueChange = onNewDetailsChange, label = { Text("Opis") })
                Text("Tip")
                listOf("turistička atrakcija", "kulturni objekat", "istorijska lokacija").forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedType == t, onClick = { onSelectedTypeChange(t) })
                        Text(t.replaceFirstChar { it.uppercase() })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { addImageLauncher.launch("image/*") }) { Text("Izaberi sliku") }
                    if (newImageUri != null) {
                        Image(painter = rememberAsyncImagePainter(newImageUri), contentDescription = null)
                    }
                }
                questions.forEachIndexed { index, qa ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(value = qa.text, onValueChange = { v -> onChangeQuestionText(index, v) }, label = { Text("Pitanje") })
                        (0..2).forEach { optIdx ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = qa.correctIndex == optIdx, onClick = { onChangeQuestionCorrectIndex(index, optIdx) })
                                val optText = qa.options.getOrNull(optIdx) ?: ""
                                OutlinedTextField(value = optText, onValueChange = { v -> onChangeQuestionOption(index, optIdx, v) }, label = { Text("Opcija ${optIdx + 1}") })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onRemoveQuestion(index) }) { Text("Ukloni") }
                        }
                    }
                }
                Button(onClick = onAddQuestion) { Text("Dodaj pitanje") }
            }
        }
    )
}
