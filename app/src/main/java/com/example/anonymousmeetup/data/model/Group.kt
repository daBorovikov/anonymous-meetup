package com.example.anonymousmeetup.data.model

data class Group(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val isPrivate: Boolean = false,
    val groupHash: String = "",
    val poolId: String = "",
    val joinToken: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
