package io.josrom.whereismycar

import java.util.Date

data class CarLocation(
    val id: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val date: Date
)
