package io.josrom.whereismycar.api

/** Models of the Where Is My Car API. Fields the API may omit are nullable (Gson ignores Kotlin nullability). */

data class ApiUser(
    val uuid: String,
    val email: String?,
    val displayName: String?
)

data class ApiCategory(
    val id: Int,
    val label: String,
    val icon: String,
    val color: String
)

data class CategoryBody(
    val label: String,
    val icon: String,
    val color: String
)

data class ApiSavedLocation(
    val id: Int,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val city: String?,
    val address: String?,
    val addressData: Map<String, String>?,
    val date: String?,
    val note: String?,
    val categoryId: Int?
)

data class SavedLocationBody(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val city: String?,
    val address: String?,
    val addressData: Map<String, String>?,
    val date: String?,
    val note: String?,
    val categoryId: Int?
)
