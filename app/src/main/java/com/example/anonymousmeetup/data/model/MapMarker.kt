package com.example.anonymousmeetup.data.model

data class MapMarker(
    val latitude: Double,
    val longitude: Double,
    val title: String? = null,
    val color: String? = null,
    val size: String? = "medium"
) 

