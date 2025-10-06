package com.example.touristquiz.ui.map.model

data class QItem(
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    val id: String = java.util.UUID.randomUUID().toString()
)
