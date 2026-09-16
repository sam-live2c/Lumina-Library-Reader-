package com.example.data.model

data class CustomFilter(
    val id: String,
    val name: String,
    val bookIds: Set<Long> = emptySet(),
    val createdAt: Long = System.currentTimeMillis()
)
