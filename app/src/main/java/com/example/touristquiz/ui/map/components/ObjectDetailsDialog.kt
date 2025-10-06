package com.example.touristquiz.ui.map.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.HorizontalDivider
import coil.compose.rememberAsyncImagePainter
import com.example.touristquiz.data.ObjectRepository

@Composable
fun ObjectDetailsDialog(
    visible: Boolean,
    name: String,
    ownerDisplay: String,
    ownerUid: String?,
    type: String?,
    imageUrl: String?,
    details: String,
    questions: List<ObjectRepository.Question>,
    currentUserUid: String?,
    answeredIds: Set<String>,
    onSubmitAnswer: (questionId: String, selectedIndex: Int, onResult: (ObjectRepository.AnswerResult) -> Unit) -> Unit,
    onAddQuestion: (ObjectRepository.CreateQuestion) -> Unit,
    onRateQuestion: (questionId: String, rating: Int) -> Unit,
    onDeleteObject: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    var showAddQuestionDialog by remember { mutableStateOf(false) }
    var newQText by remember { mutableStateOf("") }
    var newQOptions by remember { mutableStateOf(listOf("", "", "")) }
    var newQCorrect by remember { mutableStateOf(0) }

    // Track selected index per question and answer result to control UI
    val selectedIdxMap = remember { mutableStateMapOf<String, Int>() }
    val answerStatus = remember { mutableStateMapOf<String, ObjectRepository.AnswerResult>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("Zatvori") } },
        title = { Text(name) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (imageUrl != null) {
                    Image(
                        painter = rememberAsyncImagePainter(imageUrl),
                        contentDescription = null,
                        modifier = Modifier
                            .height(180.dp)
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
                Text("By: $ownerDisplay")
                type?.let { Text("Type: ${it.replaceFirstChar { c -> c.uppercase() }}") }
                Text(details)
                if (questions.isNotEmpty()) {
                    HorizontalDivider()
                }
                questions.forEachIndexed { idx, q ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${idx + 1}. ${q.text}")
                        val isCreator = currentUserUid != null && currentUserUid == (q.creatorUid ?: ownerUid)
                        val status = answerStatus[q.id]
                        val alreadyAnswered = answeredIds.contains(q.id)
                        val alreadyCorrect = alreadyAnswered || status == ObjectRepository.AnswerResult.Correct || status == ObjectRepository.AnswerResult.AlreadyAnswered
                        val canAnswer = !isCreator && !alreadyCorrect && q.options.size == 3 && currentUserUid != null

                        if (canAnswer) {
                            (0..2).forEach { i ->
                                val selected = selectedIdxMap[q.id] ?: -1
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selected == i, onClick = { selectedIdxMap[q.id] = i })
                                    Text(q.options[i])
                                }
                            }
                            Button(onClick = {
                                val sel = selectedIdxMap[q.id]
                                if (sel != null && sel in 0..2) {
                                    onSubmitAnswer(q.id, sel) { res ->
                                        answerStatus[q.id] = res
                                        if (res == ObjectRepository.AnswerResult.Correct || res == ObjectRepository.AnswerResult.AlreadyAnswered) {
                                            q.correctIndex?.let { selectedIdxMap[q.id] = it }
                                        }
                                    }
                                }
                            }) { Text("Submit") }
                        } else {
                            // Show only the correct option if answered correctly or already answered
                            if ((alreadyCorrect || alreadyAnswered) && q.correctIndex != null && q.options.size == 3) {
                                val ci = q.correctIndex
                                Text("Correct: ${q.options[ci]}")
                            } else if (isCreator) {
                                Text("(You created this question)")
                            }
                        }

                        // Rating UI: only if not creator and not already rated by this user
                        val alreadyRated = q.ratings.containsKey(currentUserUid)
                        val creatorBlockUid = q.creatorUid ?: ownerUid
                        if (currentUserUid != null && (creatorBlockUid == null || currentUserUid != creatorBlockUid) && !alreadyRated) {
                            var rating by remember(q.id) { mutableStateOf(0) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Rate:")
                                (1..5).forEach { r ->
                                    Button(onClick = { rating = r; onRateQuestion(q.id, r) }, enabled = rating == 0) { Text(r.toString()) }
                                }
                            }
                        }
                        Text("Average rating: %.2f (%d ratings)".format(q.averageRating, q.numRatings))
                    }
                    HorizontalDivider()
                }
                // Owner actions
                if (currentUserUid != null && ownerUid != null && currentUserUid == ownerUid) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAddQuestionDialog = true }) { Text("Add Question") }
                        Button(onClick = onDeleteObject) { Text("Delete object") }
                    }
                }
            }
        }
    )

    // Add Question Dialog
    if (showAddQuestionDialog) {
        AlertDialog(
            onDismissRequest = { showAddQuestionDialog = false },
            confirmButton = {
                Button(onClick = {
                    if (newQText.isNotBlank() && newQOptions.all { it.isNotBlank() }) {
                        onAddQuestion(ObjectRepository.CreateQuestion(newQText, newQOptions, newQCorrect))
                        showAddQuestionDialog = false
                        newQText = ""
                        newQOptions = listOf("", "", "")
                        newQCorrect = 0
                    }
                }) { Text("Add") }
            },
            title = { Text("Add Question") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newQText, onValueChange = { newQText = it }, label = { Text("Question") })
                    (0..2).forEach { i ->
                        OutlinedTextField(value = newQOptions[i], onValueChange = {
                            newQOptions = newQOptions.toMutableList().apply { set(i, it) }
                        }, label = { Text("Option ${i+1}") })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Correct Answer:")
                        (0..2).forEach { i ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = newQCorrect == i, onClick = { newQCorrect = i })
                                Text("${i+1}")
                            }
                        }
                    }
                }
            }
        )
    }
}
