package io.josrom.whereismycar

data class SavedLocation(
    val id: Int,
    val address: HashMap<String, String>,
    val displayName: String,
    val city: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val date: String
)
