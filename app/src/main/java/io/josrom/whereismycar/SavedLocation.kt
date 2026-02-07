package io.josrom.whereismycar

data class SavedLocation(
    val id: Int = 0,
    val address: HashMap<String, String> = hashMapOf(),
    val displayName: String = "",
    val city: String = "",
    val country: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val date: String = ""
)
