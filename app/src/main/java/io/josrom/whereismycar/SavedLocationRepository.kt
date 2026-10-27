package io.josrom.whereismycar

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.location.Address
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.osmdroid.api.IGeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedLocationRepository(private val context: Context) {

    private val dbHelper = DBHelper(context, null)
    private val gson = Gson()
    private var syncManager: SyncManager? = null

    fun setSyncManager(syncManager: SyncManager) {
        this.syncManager = syncManager
    }

    fun getAllLocations(
        limit: Int? = null,
        offset: Int? = null,
        searchQuery: String? = null,
        categoryIds: List<Int>? = null,
        startDate: String? = null,
        endDate: String? = null,
        withCategory: Boolean = false
    ): MutableList<SavedLocation> {
        val savedLocations = mutableListOf<SavedLocation>()
        val db = dbHelper.readableDatabase

        val selectionParts = mutableListOf<String>()
        val selectionArgsList = mutableListOf<String>()

        if (!searchQuery.isNullOrBlank()) {
            selectionParts.add("(${SavedLocation.COLUMN_DISPLAY_NAME} LIKE ? OR ${SavedLocation.COLUMN_ADDRESS} LIKE ?)")
            selectionArgsList.add("%$searchQuery%")
            selectionArgsList.add("%$searchQuery%")
        }

        if (!categoryIds.isNullOrEmpty()) {
            val hasUncategorized = categoryIds.contains(-1)
            val actualCategoryIds = categoryIds.filter { it != -1 }
            
            val categorySelection = mutableListOf<String>()
            if (actualCategoryIds.isNotEmpty()) {
                val placeholders = actualCategoryIds.joinToString(",") { "?" }
                categorySelection.add("${SavedLocation.COLUMN_CATEGORY_ID} IN ($placeholders)")
                actualCategoryIds.forEach { selectionArgsList.add(it.toString()) }
            }
            if (hasUncategorized) {
                categorySelection.add("${SavedLocation.COLUMN_CATEGORY_ID} IS NULL")
            }
            
            if (categorySelection.isNotEmpty()) {
                selectionParts.add("(" + categorySelection.joinToString(" OR ") + ")")
            }
        }

        if (!startDate.isNullOrBlank()) {
            selectionParts.add("${SavedLocation.COLUMN_DATE} >= ?")
            selectionArgsList.add(startDate)
        }

        if (!endDate.isNullOrBlank()) {
            selectionParts.add("${SavedLocation.COLUMN_DATE} <= ?")
            selectionArgsList.add(endDate)
        }

        val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
        val selectionArgs = if (selectionArgsList.isNotEmpty()) selectionArgsList.toTypedArray() else null

        val limitStr = if (limit != null) {
            if (offset != null) "$offset, $limit" else "$limit"
        } else {
            null
        }

        val tableName = if (withCategory) {
            "${SavedLocation.TABLE_NAME} LEFT JOIN ${Category.TABLE_NAME} ON ${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_CATEGORY_ID} = ${Category.TABLE_NAME}.${Category.COLUMN_ID}"
        } else {
            SavedLocation.TABLE_NAME
        }

        val columns = if (withCategory) {
            arrayOf(
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_ID}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_DISPLAY_NAME}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_COUNTRY}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_CITY}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_ADDRESS}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_ADDRESS_DATA}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_LATITUDE}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_LONGITUDE}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_DATE}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_CATEGORY_ID}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_NOTE}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_REMOTE_ID}",
                Category.COLUMN_LABEL,
                Category.COLUMN_COLOR,
                Category.COLUMN_ICON
            )
        } else null

        val cursor = db.query(
            tableName,
            columns,
            selection,
            selectionArgs,
            null,
            null,
            "${SavedLocation.COLUMN_DATE} DESC",
            limitStr
        )

        with(cursor) {
            if (moveToFirst()) {
                do {
                    try {
                        savedLocations.add(mapDataToSavedLocation(this, withCategory))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } while (moveToNext())
            }
        }

        cursor.close()
        return savedLocations
    }

    fun getLocation(id: Int, withCategory: Boolean = false): SavedLocation? {
        val db = dbHelper.readableDatabase
        val tableName = if (withCategory) {
            "${SavedLocation.TABLE_NAME} LEFT JOIN ${Category.TABLE_NAME} ON ${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_CATEGORY_ID} = ${Category.TABLE_NAME}.${Category.COLUMN_ID}"
        } else {
            SavedLocation.TABLE_NAME
        }

        val columns = if (withCategory) {
            arrayOf(
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_ID}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_DISPLAY_NAME}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_COUNTRY}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_CITY}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_ADDRESS}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_ADDRESS_DATA}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_LATITUDE}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_LONGITUDE}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_DATE}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_CATEGORY_ID}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_NOTE}",
                "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_REMOTE_ID}",
                Category.COLUMN_LABEL,
                Category.COLUMN_COLOR,
                Category.COLUMN_ICON
            )
        } else null

        val cursor = db.query(
            tableName,
            columns,
            "${SavedLocation.TABLE_NAME}.${SavedLocation.COLUMN_ID} = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )

        var savedLocation: SavedLocation? = null
        if (cursor.moveToFirst()) {
            savedLocation = mapDataToSavedLocation(cursor, withCategory)
        }
        cursor.close()
        return savedLocation
    }

    private fun mapDataToSavedLocation(cursor: Cursor, withCategory: Boolean): SavedLocation {
        val id = cursor.getInt(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_ID))
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_DISPLAY_NAME)) ?: ""
        val address = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_ADDRESS)) ?: ""
        val city = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_CITY)) ?: ""
        val country = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_COUNTRY)) ?: ""
        val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_LATITUDE))
        val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_LONGITUDE))
        val date = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_DATE)) ?: ""
        val categoryId = if (cursor.isNull(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_CATEGORY_ID))) null else cursor.getInt(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_CATEGORY_ID))
        val note = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_NOTE))
        
        val addressDataJson = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_ADDRESS_DATA))
        val addressData: HashMap<String, String> = if (addressDataJson != null) {
            val type = object : TypeToken<HashMap<String, String>>() {}.type
            gson.fromJson(addressDataJson, type)
        } else {
            hashMapOf()
        }

        var category: Category? = null
        if (withCategory && categoryId != null) {
            category = Category(
                id = categoryId,
                label = cursor.getString(cursor.getColumnIndexOrThrow(Category.COLUMN_LABEL)) ?: "",
                icon = cursor.getString(cursor.getColumnIndexOrThrow(Category.COLUMN_ICON)) ?: "",
                color = cursor.getString(cursor.getColumnIndexOrThrow(Category.COLUMN_COLOR)) ?: ""
            )
        }

        val remoteIdIndex = cursor.getColumnIndex(SavedLocation.COLUMN_REMOTE_ID)
        val remoteId = if (remoteIdIndex < 0 || cursor.isNull(remoteIdIndex)) null else cursor.getInt(remoteIdIndex)

        return SavedLocation(id, displayName, address, city, country, latitude, longitude, date, categoryId, note, addressData, category, remoteId)
    }

    fun insertLocation(displayName: String, location: IGeoPoint, categoryId: Int? = null, note: String? = null): Int {
        return insertLocation(displayName, displayName, "", "", location.latitude, location.longitude, hashMapOf(), categoryId, note)
    }

    fun insertLocation(address: Address, location: IGeoPoint, categoryId: Int? = null, note: String? = null): Int {
        val addressLines = mutableListOf<String>()
        if (address.maxAddressLineIndex >= 0) {
            for (i in 1..address.maxAddressLineIndex) {
                addressLines.add(address.getAddressLine(i))
            }
        }
        val addressLine = addressLines.joinToString(", ")
        val displayName = address.getAddressLine(0) ?: ""

        return insertLocation(addressLine, displayName, address.locality ?: "", address.countryName ?: "", location.latitude, location.longitude, getFormattedAddress(address), categoryId, note)
    }

    private fun getFormattedAddress(address: Address): HashMap<String, String> {
        val addressParts = hashMapOf<String, String>()

        if (address.maxAddressLineIndex >= 0) {
            for (i in 0..address.maxAddressLineIndex) {
                addressParts["Address Line $i"] = address.getAddressLine(i) ?: ""
            }
        }

        address.locality?.let { addressParts["Locality"] = it }
        address.subLocality?.let { addressParts["Sub Locality"] = it }
        address.adminArea?.let { addressParts["Admin Area"] = it }
        address.subAdminArea?.let { addressParts["Sub Admin Area"] = it }
        address.postalCode?.let { addressParts["Postal Code"] = it }
        address.countryName?.let { addressParts["Country Name"] = it }
        address.countryCode?.let { addressParts["Country Code"] = it }
        address.featureName?.let { addressParts["Feature Name"] = it }
        address.thoroughfare?.let { addressParts["Thoroughfare"] = it }
        address.subThoroughfare?.let { addressParts["Sub Thoroughfare"] = it }
        address.phone?.let { addressParts["Phone"] = it }
        address.locale?.let { addressParts["Locale"] = it.toString() }
        address.premises?.let { addressParts["Premises"] = it }
        address.url?.let { addressParts["URL"] = it }

        addressParts["Latitude"] = address.latitude.toString()
        addressParts["Longitude"] = address.longitude.toString()

        address.extras?.let { extras ->
            for (key in extras.keySet()) {
                @Suppress("DEPRECATION")
                val value = extras.get(key)
                if (value != null) {
                    addressParts[key] = value.toString()
                }
            }
        }

        return addressParts
    }

    /** A location always has a category: without one, the default category is used. */
    fun insertLocation(address: String, displayName: String, city: String, country: String, latitude: Double, longitude: Double, addressMap: HashMap<String, String>, categoryId: Int? = null, note: String? = null): Int {
        val categoryId = categoryId ?: CategoryRepository(context).getOrCreateDefaultCategory().id
        val dateString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val values = ContentValues().apply {
            put(SavedLocation.COLUMN_DISPLAY_NAME, displayName)
            put(SavedLocation.COLUMN_ADDRESS, address)
            put(SavedLocation.COLUMN_CITY, city)
            put(SavedLocation.COLUMN_COUNTRY, country)
            put(SavedLocation.COLUMN_LATITUDE, latitude)
            put(SavedLocation.COLUMN_LONGITUDE, longitude)
            put(SavedLocation.COLUMN_DATE, dateString)
            put(SavedLocation.COLUMN_CATEGORY_ID, categoryId)
            put(SavedLocation.COLUMN_NOTE, note)
            put(SavedLocation.COLUMN_ADDRESS_DATA, gson.toJson(addressMap))
        }

        val db = dbHelper.writableDatabase
        val newId = db.insert(SavedLocation.TABLE_NAME, null, values).toInt()
        
        // Auto-sync if enabled
        if (shouldSyncLocation(categoryId)) {
            val location = SavedLocation(
                id = newId,
                displayName = displayName,
                address = address,
                city = city,
                country = country,
                latitude = latitude,
                longitude = longitude,
                date = dateString,
                categoryId = categoryId,
                note = note,
                addressData = addressMap
            )
            syncManager?.uploadLocation(location)
        }
        
        return newId
    }

    fun updateLocation(id: Int, displayName: String, categoryId: Int, note: String?) {
        val values = ContentValues().apply {
            put(SavedLocation.COLUMN_DISPLAY_NAME, displayName)
            put(SavedLocation.COLUMN_CATEGORY_ID, categoryId)
            put(SavedLocation.COLUMN_NOTE, note)
        }

        val db = dbHelper.writableDatabase
        db.update(SavedLocation.TABLE_NAME, values, "${SavedLocation.COLUMN_ID} = ?", arrayOf(id.toString()))
        
        // Auto-sync if enabled
        if (shouldSyncLocation(categoryId)) {
            getLocation(id)?.let { location ->
                syncManager?.uploadLocation(location)
            }
        }
    }

    fun deleteLocation(id: Int) {
        // Get location before deleting for sync
        val locationToDelete = getLocation(id)
        
        val db = dbHelper.writableDatabase
        db.delete(SavedLocation.TABLE_NAME, "${SavedLocation.COLUMN_ID} = ?", arrayOf(id.toString()))
        
        // Auto-sync if enabled (only locations already in the API have something to delete)
        val remoteId = locationToDelete?.remoteId
        if (remoteId != null && shouldSyncLocation(locationToDelete.categoryId)) {
            syncManager?.deleteFromCloud(remoteId)
        }
    }

    // Sync with the API. These methods never trigger an upload themselves.

    fun setRemoteId(locationId: Int, remoteId: Int?) {
        val values = ContentValues().apply {
            if (remoteId == null) putNull(SavedLocation.COLUMN_REMOTE_ID) else put(SavedLocation.COLUMN_REMOTE_ID, remoteId)
        }
        dbHelper.writableDatabase.update(SavedLocation.TABLE_NAME, values, "${SavedLocation.COLUMN_ID} = ?", arrayOf(locationId.toString()))
    }

    fun clearAllRemoteIds() {
        val values = ContentValues().apply { putNull(SavedLocation.COLUMN_REMOTE_ID) }
        dbHelper.writableDatabase.update(SavedLocation.TABLE_NAME, values, null, null)
    }

    /** Inserts a location downloaded from the API, already linked. Returns the local id. */
    fun insertFromRemote(location: SavedLocation): Int {
        val values = ContentValues().apply {
            put(SavedLocation.COLUMN_DISPLAY_NAME, location.displayName)
            put(SavedLocation.COLUMN_ADDRESS, location.address)
            put(SavedLocation.COLUMN_CITY, location.city)
            put(SavedLocation.COLUMN_COUNTRY, location.country)
            put(SavedLocation.COLUMN_LATITUDE, location.latitude)
            put(SavedLocation.COLUMN_LONGITUDE, location.longitude)
            put(SavedLocation.COLUMN_DATE, location.date)
            put(SavedLocation.COLUMN_CATEGORY_ID, location.categoryId)
            put(SavedLocation.COLUMN_NOTE, location.note)
            put(SavedLocation.COLUMN_ADDRESS_DATA, gson.toJson(location.addressData))
            put(SavedLocation.COLUMN_REMOTE_ID, location.remoteId)
        }
        return dbHelper.writableDatabase.insert(SavedLocation.TABLE_NAME, null, values).toInt()
    }

    private fun shouldSyncLocation(categoryId: Int?): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val autoSync = prefs.getBoolean("sync_auto", true)
        if (!autoSync) return false
        
        val syncAllCategories = prefs.getBoolean("sync_all_categories", true)
        if (syncAllCategories) return true
        
        // Check if category is in selected categories
        val selectedJson = prefs.getString("sync_selected_categories", "[]") ?: "[]"
        return try {
            val arr = JSONArray(selectedJson)
            val selectedIds = MutableList(arr.length()) { i -> arr.getInt(i) }
            categoryId == null || selectedIds.contains(categoryId)
        } catch (_: Exception) {
            false
        }
    }
}
