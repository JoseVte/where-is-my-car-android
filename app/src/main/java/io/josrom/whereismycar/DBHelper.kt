package io.josrom.whereismycar

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Address
import org.json.JSONObject
import org.osmdroid.api.IGeoPoint

class DBHelper(private val context: Context, factory: SQLiteDatabase.CursorFactory?) : SQLiteOpenHelper(context, DATABASE_NAME, factory, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase?) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_DISPLAY_NAME TEXT,
                $COLUMN_CITY TEXT,
                $COLUMN_COUNTRY TEXT,
                $COLUMN_ADDRESS JSON,
                $COLUMN_LATITUDE DOUBLE,
                $COLUMN_LONGITUDE DOUBLE,
                $COLUMN_DATE DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()

        db?.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun getAllLocations(limit: Int? = null, offset: Int? = null): List<SavedLocation> {
        var query = "SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_DATE DESC"

        if (limit != null) {
            query += " LIMIT $limit"
        }

        if (offset != null) {
            query += " OFFSET $offset"
        }

        var savedLocations = listOf<SavedLocation>()
        readableDatabase.rawQuery(query, null).use {
            if (it.moveToFirst()) {
                do {
                    try {
                        savedLocations = savedLocations.plus(
                            this.mapDataToSavedLocation(it)
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } while (it.moveToNext())
            }
        }

        return savedLocations
    }


    fun insertLocation(location: IGeoPoint) {
        this.insertLocation(hashMapOf<String, String>(), "", "", "", location.latitude, location.longitude)
    }

    fun insertLocation(displayName: String, location: IGeoPoint) {
        this.insertLocation(hashMapOf<String, String>(), displayName, "", "", location.latitude, location.longitude)
    }

    fun insertLocation(address: Address, location: IGeoPoint) {
        this.insertLocation(this.getFormattedAddress(address), address.getAddressLine(0), address.locality, address.countryName, location.latitude, location.longitude)
    }

    private fun getFormattedAddress(address: Address): HashMap<String, String> {
        val addressParts = hashMapOf<String, String>()

        if (address.maxAddressLineIndex > 0) {
            for (i in 0..address.maxAddressLineIndex) {
                addressParts["Address Line $i"] = address.getAddressLine(i)
            }
        }

        if (address.locality != null) {
            addressParts["Locality"] = address.locality
        }

        if (address.subLocality != null) {
            addressParts["Sub Locality"] = address.subLocality
        }

        if (address.adminArea != null) {
            addressParts["Admin Area"] = address.adminArea
        }

        if (address.subAdminArea != null) {
            addressParts["Sub Admin Area"] = address.subAdminArea
        }

        if (address.postalCode != null) {
            addressParts["Postal Code"] = address.postalCode
        }

        if (address.countryName != null) {
            addressParts["Country Name"] = address.countryName
        }

        if (address.countryCode != null) {
            addressParts["Country Code"] = address.countryCode
        }

        if (address.featureName != null) {
            addressParts["Feature Name"] = address.featureName
        }

        if (address.thoroughfare != null) {
            addressParts["Thoroughfare"] = address.thoroughfare
        }

        if (address.subThoroughfare != null) {
            addressParts["Sub Thoroughfare"] = address.subThoroughfare
        }

        if (address.phone != null) {
            addressParts["Phone"] = address.phone
        }

        if (address.locale != null) {
            addressParts["Locale"] = address.locale.toString()
        }

        if (address.premises != null) {
            addressParts["Premises"] = address.premises
        }

        if (address.url != null) {
            addressParts["URL"] = address.url
        }

        addressParts["Latitude"] = address.latitude.toString()
        addressParts["Longitude"] = address.longitude.toString()

        if (address.extras != null) {
            for (key in address.extras.keySet()) {
                if (address.extras.getString(key) != null)  {
                    addressParts[key] = address.extras.getString(key)!!
                }
            }
        }

        return addressParts
    }

    fun insertLocation(address: HashMap<String, String>, displayName: String, city: String, country: String, location: IGeoPoint) {
        this.insertLocation(address, displayName, city, country, location.latitude, location.longitude)
    }

    fun insertLocation(address: HashMap<String, String>, displayName: String, city: String, country: String, latitude: Double, longitude: Double) {
        val values  = ContentValues().apply {
            put(COLUMN_ADDRESS, JSONObject(address as Map<*, *>).toString())
            put(COLUMN_DISPLAY_NAME, displayName)
            put(COLUMN_CITY, city)
            put(COLUMN_COUNTRY, country)
            put(COLUMN_LATITUDE, latitude)
            put(COLUMN_LONGITUDE, longitude)
        }

        writableDatabase.insert(TABLE_NAME, null, values)
        SyncManager(context).sync()
    }

    fun updateLocation(id: Int, address: String, location: IGeoPoint) {
        this.updateLocation(id, hashMapOf<String, String>(), address, "", "", location.latitude, location.longitude)
    }

    fun updateLocation(id: Int, address: HashMap<String, String>, displayName: String, city: String, country: String, latitude: Double, longitude: Double) {
        val values = ContentValues().apply {
            put(COLUMN_ADDRESS, JSONObject(address as Map<*, *>).toString())
            put(COLUMN_DISPLAY_NAME, displayName)
            put(COLUMN_CITY, city)
            put(COLUMN_COUNTRY, country)
            put(COLUMN_LATITUDE, latitude)
            put(COLUMN_LONGITUDE, longitude)
        }

        writableDatabase.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
        SyncManager(context).sync()
    }

    fun deleteLocation(id: Int) {
        writableDatabase.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
        SyncManager(context).sync()
    }

    fun getLocation(locationId: Int): SavedLocation? {
        val cursor = readableDatabase.query(TABLE_NAME, null, "$COLUMN_ID = ?", arrayOf(locationId.toString()), null, null, null)

        if (cursor.moveToFirst()) {
            return this.mapDataToSavedLocation(cursor)
        }
        cursor.close()

        return null
    }

    fun mapDataToSavedLocation(cursor: Cursor): SavedLocation {
        val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DISPLAY_NAME))
        val city = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CITY))
        val country = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COUNTRY))
        val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE))
        val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE))
        val date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE))

        val addressJson = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_ADDRESS))
        val addressMap = HashMap<String, String>()
        if (addressJson != null) {
            val jsonObject = JSONObject(addressJson)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                addressMap[key] = jsonObject.getString(key)
            }
        }

        return SavedLocation(id, addressMap, displayName, city, country, latitude, longitude, date)
    }

    companion object {
        private const val DATABASE_NAME = "whereismycar_database"
        private const val DATABASE_VERSION = 1

        const val TABLE_NAME = "locations"
        const val COLUMN_ID = "id"
        const val COLUMN_DISPLAY_NAME = "display_name"
        const val COLUMN_COUNTRY = "country"
        const val COLUMN_CITY = "city"
        const val COLUMN_ADDRESS = "address"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_DATE = "date"
    }
}
