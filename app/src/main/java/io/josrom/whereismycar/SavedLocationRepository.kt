package io.josrom.whereismycar

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.location.Address
import org.json.JSONObject
import org.osmdroid.api.IGeoPoint

class SavedLocationRepository(private val context: Context) {
    private val dbHelper = DBHelper(context, null)

    fun getAllLocations(limit: Int? = null, offset: Int? = null, query: String? = null): MutableList<SavedLocation> {
        var queryString = "SELECT * FROM ${SavedLocation.TABLE_NAME}"
        val selectionArgs = mutableListOf<String>()

        if (!query.isNullOrEmpty()) {
            queryString += " WHERE ${SavedLocation.COLUMN_DISPLAY_NAME} LIKE ? OR ${SavedLocation.COLUMN_ADDRESS} LIKE ? OR ${SavedLocation.COLUMN_CITY} LIKE ? OR ${SavedLocation.COLUMN_COUNTRY} LIKE ?"
            selectionArgs.add("%${query}%")
            selectionArgs.add("%${query}%")
            selectionArgs.add("%${query}%")
            selectionArgs.add("%${query}%")
        }

        queryString += " ORDER BY ${SavedLocation.COLUMN_DATE} DESC"

        if (limit != null) {
            queryString += " LIMIT $limit"
        }

        if (offset != null) {
            queryString += " OFFSET $offset"
        }

        val savedLocations: MutableList<SavedLocation> = mutableListOf()
        dbHelper.readableDatabase.rawQuery(queryString, selectionArgs.toTypedArray()).use {
            if (it.moveToFirst()) {
                do {
                    try {
                        savedLocations.add(mapDataToSavedLocation(it))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } while (it.moveToNext())
            }
        }

        return savedLocations
    }

    fun insertLocation(location: IGeoPoint, categoryId: Int? = null, note: String? = null) {
        insertLocation("", "", "", "", location.latitude, location.longitude, hashMapOf<String, String>(), categoryId, note)
    }

    fun insertLocation(displayName: String, location: IGeoPoint, categoryId: Int? = null, note: String? = null) {
        insertLocation(displayName, displayName, "", "", location.latitude, location.longitude, hashMapOf<String, String>(), categoryId, note)
    }

    fun insertLocation(address: Address, location: IGeoPoint, categoryId: Int? = null, note: String? = null) {
        insertLocation(address.getAddressLine(0), address.getAddressLine(0), address.locality, address.countryName, location.latitude, location.longitude, getFormattedAddress(address), categoryId, note)
    }

    private fun getFormattedAddress(address: Address): HashMap<String, String> {
        val addressParts = hashMapOf<String, String>()

        if (address.maxAddressLineIndex > 0) {
            for (i in 0..address.maxAddressLineIndex) {
                addressParts["Address Line $i"] = address.getAddressLine(i)
            }
        }

        if (address.locality != null) addressParts["Locality"] = address.locality
        if (address.subLocality != null) addressParts["Sub Locality"] = address.subLocality
        if (address.adminArea != null) addressParts["Admin Area"] = address.adminArea
        if (address.subAdminArea != null) addressParts["Sub Admin Area"] = address.subAdminArea
        if (address.postalCode != null) addressParts["Postal Code"] = address.postalCode
        if (address.countryName != null) addressParts["Country Name"] = address.countryName
        if (address.countryCode != null) addressParts["Country Code"] = address.countryCode
        if (address.featureName != null) addressParts["Feature Name"] = address.featureName
        if (address.thoroughfare != null) addressParts["Thoroughfare"] = address.thoroughfare
        if (address.subThoroughfare != null) addressParts["Sub Thoroughfare"] = address.subThoroughfare
        if (address.phone != null) addressParts["Phone"] = address.phone
        if (address.locale != null) addressParts["Locale"] = address.locale.toString()
        if (address.premises != null) addressParts["Premises"] = address.premises
        if (address.url != null) addressParts["URL"] = address.url

        addressParts["Latitude"] = address.latitude.toString()
        addressParts["Longitude"] = address.longitude.toString()

        if (address.extras != null) {
            for (key in address.extras.keySet()) {
                if (address.extras.getString(key) != null) {
                    addressParts[key] = address.extras.getString(key)!!
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
            put(SavedLocation.COLUMN_CATEGORY_ID, categoryId)
            put(SavedLocation.COLUMN_NOTE, note)
            put(SavedLocation.COLUMN_ADDRESS_DATA, JSONObject(addressMap as Map<*, *>).toString())
        }

        dbHelper.writableDatabase.insert(SavedLocation.TABLE_NAME, null, values)
        SyncManager(context).sync()
    }

    fun updateLocation(id: Int, displayName: String, address: String, city: String, country: String, latitude: Double, longitude: Double, addressMap: HashMap<String, String>, categoryId: Int? = null, note: String? = null) {
        val values = ContentValues().apply {
            put(SavedLocation.COLUMN_DISPLAY_NAME, displayName)
            put(SavedLocation.COLUMN_ADDRESS, address)
            put(SavedLocation.COLUMN_CITY, city)
            put(SavedLocation.COLUMN_COUNTRY, country)
            put(SavedLocation.COLUMN_LATITUDE, latitude)
            put(SavedLocation.COLUMN_LONGITUDE, longitude)
            put(SavedLocation.COLUMN_CATEGORY_ID, categoryId)
            put(SavedLocation.COLUMN_NOTE, note)
            put(SavedLocation.COLUMN_ADDRESS_DATA, JSONObject(addressMap as Map<*, *>).toString())
        }

        dbHelper.writableDatabase.update(SavedLocation.TABLE_NAME, values, "${SavedLocation.COLUMN_ID} = ?", arrayOf(id.toString()))
        SyncManager(context).sync()
    }

    fun deleteLocation(id: Int) {
        dbHelper.writableDatabase.delete(SavedLocation.TABLE_NAME, "${SavedLocation.COLUMN_ID} = ?", arrayOf(id.toString()))
        SyncManager(context).sync()
    }

    fun getLocation(locationId: Int): SavedLocation? {
        val cursor = dbHelper.readableDatabase.query(SavedLocation.TABLE_NAME, null, "${SavedLocation.COLUMN_ID} = ?", arrayOf(locationId.toString()), null, null, null)

        if (cursor.moveToFirst()) {
            return mapDataToSavedLocation(cursor)
        }
        cursor.close()

        return null
    }

    fun mapDataToSavedLocation(cursor: Cursor): SavedLocation {
        val id = cursor.getInt(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_ID))
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_DISPLAY_NAME))
        val address = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_ADDRESS))
        val city = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_CITY))
        val country = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_COUNTRY))
        val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_LATITUDE))
        val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_LONGITUDE))
        val date = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_DATE))

        val addressJson = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_ADDRESS_DATA))
        val addressMap = HashMap<String, String>()
        if (addressJson != null) {
            val jsonObject = JSONObject(addressJson)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                addressMap[key] = jsonObject.getString(key)
            }
        }

        val categoryIdIndex = cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_CATEGORY_ID)
        val categoryId = if (cursor.isNull(categoryIdIndex)) null else cursor.getInt(categoryIdIndex)
        val note = cursor.getString(cursor.getColumnIndexOrThrow(SavedLocation.COLUMN_NOTE))

        return SavedLocation(id, displayName, address, city, country, latitude, longitude, date, categoryId, note, addressMap)
    }
}
