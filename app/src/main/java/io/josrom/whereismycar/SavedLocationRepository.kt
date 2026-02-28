package io.josrom.whereismycar

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.location.Address
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.api.IGeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedLocationRepository(context: Context) {

    private val dbHelper = DBHelper(context, null)
    private val gson = Gson()

    fun getAllLocations(limit: Int? = null, offset: Int? = null, searchQuery: String? = null): MutableList<SavedLocation> {
        val savedLocations = mutableListOf<SavedLocation>()
        val db = dbHelper.readableDatabase

        val selection = if (!searchQuery.isNullOrBlank()) {
            "${SavedLocation.COLUMN_DISPLAY_NAME} LIKE ? OR ${SavedLocation.COLUMN_ADDRESS} LIKE ?"
        } else {
            null
        }

        val selectionArgs = if (!searchQuery.isNullOrBlank()) {
            arrayOf("%$searchQuery%", "%$searchQuery%")
        } else {
            null
        }

        val limitStr = if (limit != null) {
            if (offset != null) "$offset, $limit" else "$limit"
        } else {
            null
        }

        val cursor = db.query(
            SavedLocation.TABLE_NAME,
            null,
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
                        savedLocations.add(mapDataToSavedLocation(this))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } while (moveToNext())
            }
        }

        cursor.close()
        return savedLocations
    }

    fun getLocation(id: Int): SavedLocation? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            SavedLocation.TABLE_NAME,
            null,
            "${SavedLocation.COLUMN_ID} = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )

        var savedLocation: SavedLocation? = null
        if (cursor.moveToFirst()) {
            savedLocation = mapDataToSavedLocation(cursor)
        }
        cursor.close()
        return savedLocation
    }

    private fun mapDataToSavedLocation(cursor: Cursor): SavedLocation {
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

        return SavedLocation(id, displayName, address, city, country, latitude, longitude, date, categoryId, note, addressData)
    }

    fun insertLocation(location: IGeoPoint, categoryId: Int? = null, note: String? = null) {
        insertLocation("", "", "", "", location.latitude, location.longitude, hashMapOf(), categoryId, note)
    }

    fun insertLocation(displayName: String, location: IGeoPoint, categoryId: Int? = null, note: String? = null) {
        insertLocation(displayName, displayName, "", "", location.latitude, location.longitude, hashMapOf(), categoryId, note)
    }

    fun insertLocation(address: Address, location: IGeoPoint, categoryId: Int? = null, note: String? = null) {
        val addressLines = mutableListOf<String>()
        if (address.maxAddressLineIndex >= 0) {
            for (i in 1..address.maxAddressLineIndex) {
                addressLines.add(address.getAddressLine(i))
            }
        }
        val addressLine = addressLines.joinToString(", ")
        val displayName = address.getAddressLine(0) ?: ""

        insertLocation(addressLine, displayName, address.locality ?: "", address.countryName ?: "", location.latitude, location.longitude, getFormattedAddress(address), categoryId, note)
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
                // Fix: Attempt to cast generated internal exception:
                // java.lang.ClassCastException: org.osmdroid.util.BoundingBox cannot be cast to java.lang.String
                // Use value.toString() instead of extras.getString(key) to avoid ClassCastException
                @Suppress("DEPRECATION")
                val value = extras.get(key)
                if (value != null) {
                    addressParts[key] = value.toString()
                }
            }
        }

        return addressParts
    }

    fun insertLocation(address: String, displayName: String, city: String, country: String, location: IGeoPoint, addressMap: HashMap<String, String>, categoryId: Int? = null, note: String? = null) {
        insertLocation(address, displayName, city, country, location.latitude, location.longitude, addressMap, categoryId, note)
    }

    fun insertLocation(address: String, displayName: String, city: String, country: String, latitude: Double, longitude: Double, addressMap: HashMap<String, String>, categoryId: Int? = null, note: String? = null) {
        val values = ContentValues().apply {
            put(SavedLocation.COLUMN_DISPLAY_NAME, displayName)
            put(SavedLocation.COLUMN_ADDRESS, address)
            put(SavedLocation.COLUMN_CITY, city)
            put(SavedLocation.COLUMN_COUNTRY, country)
            put(SavedLocation.COLUMN_LATITUDE, latitude)
            put(SavedLocation.COLUMN_LONGITUDE, longitude)
            put(SavedLocation.COLUMN_DATE, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            put(SavedLocation.COLUMN_CATEGORY_ID, categoryId)
            put(SavedLocation.COLUMN_NOTE, note)
            put(SavedLocation.COLUMN_ADDRESS_DATA, gson.toJson(addressMap))
        }

        val db = dbHelper.writableDatabase
        db.insert(SavedLocation.TABLE_NAME, null, values)
    }

    fun deleteLocation(id: Int) {
        val db = dbHelper.writableDatabase
        db.delete(SavedLocation.TABLE_NAME, "${SavedLocation.COLUMN_ID} = ?", arrayOf(id.toString()))
    }

    fun updateLocation(id: Int, displayName: String, categoryId: Int?, note: String?) {
        val values = ContentValues().apply {
            put(SavedLocation.COLUMN_DISPLAY_NAME, displayName)
            put(SavedLocation.COLUMN_CATEGORY_ID, categoryId)
            put(SavedLocation.COLUMN_NOTE, note)
        }

        val db = dbHelper.writableDatabase
        db.update(SavedLocation.TABLE_NAME, values, "${SavedLocation.COLUMN_ID} = ?", arrayOf(id.toString()))
    }
}
