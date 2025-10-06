package com.example.touristquiz.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.touristquiz.data.ImageRepository
import com.example.touristquiz.data.ObjectRepository
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun LeaderboardScreen(imageRepository: ImageRepository, onClose: () -> Unit = {}) {
    val repo = remember { ObjectRepository(FirebaseFirestore.getInstance(), imageRepository) }
    var items by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }

    DisposableEffect(Unit) {
        val reg = repo.listenLeaderboard { list -> items = list }
        onDispose { reg.remove() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Leaderboard", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Zatvori") }
            }
            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                itemsIndexed(items) { index, (name, points) ->
                    Text(text = "${index + 1}. $name — $points pts", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
