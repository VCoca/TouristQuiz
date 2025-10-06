package com.example.touristquiz.ui.map.model

data class QItem(
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    // Stable identifier for Compose keys; default to a random UUID
    val id: String = java.util.UUID.randomUUID().toString()
)
