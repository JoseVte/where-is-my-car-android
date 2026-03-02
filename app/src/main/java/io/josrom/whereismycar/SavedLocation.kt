package io.josrom.whereismycar

data class SavedLocation(
    val id: Int = 0,
    val displayName: String = "",
    val address: String = "",
    val city: String = "",
    val country: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val date: String = "",
    val categoryId: Int? = null,
    val note: String? = null,
    val addressData: HashMap<String, String> = hashMapOf(),
    val category: Category? = null
) {
    companion object {
        const val TABLE_NAME = "locations"
        const val COLUMN_ID = "id"
        const val COLUMN_DISPLAY_NAME = "display_name"
        const val COLUMN_COUNTRY = "country"
        const val COLUMN_CITY = "city"
        const val COLUMN_ADDRESS = "address"
        const val COLUMN_ADDRESS_DATA = "address_data"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_DATE = "date"
        const val COLUMN_CATEGORY_ID = "category_id"
        const val COLUMN_NOTE = "note"
    }
}
