package io.josrom.whereismycar

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.content.ContextCompat

class DBHelper(private val context: Context, factory: SQLiteDatabase.CursorFactory?) : SQLiteOpenHelper(context, DATABASE_NAME, factory, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase?) {
        val createLocationsTable = """
            CREATE TABLE ${SavedLocation.TABLE_NAME} (
                ${SavedLocation.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${SavedLocation.COLUMN_DISPLAY_NAME} TEXT,
                ${SavedLocation.COLUMN_ADDRESS} TEXT,
                ${SavedLocation.COLUMN_CITY} TEXT,
                ${SavedLocation.COLUMN_COUNTRY} TEXT,
                ${SavedLocation.COLUMN_LATITUDE} DOUBLE,
                ${SavedLocation.COLUMN_LONGITUDE} DOUBLE,
                ${SavedLocation.COLUMN_DATE} DATETIME DEFAULT CURRENT_TIMESTAMP,
                ${SavedLocation.COLUMN_CATEGORY_ID} INTEGER,
                ${SavedLocation.COLUMN_NOTE} TEXT,
                ${SavedLocation.COLUMN_ADDRESS_DATA} JSON
            )
        """.trimIndent()

        val createCategoriesTable = """
            CREATE TABLE ${Category.TABLE_NAME} (
                ${Category.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Category.COLUMN_LABEL} TEXT UNIQUE,
                ${Category.COLUMN_ICON} TEXT,
                ${Category.COLUMN_COLOR} TEXT
            )
        """.trimIndent()

        db?.execSQL(createLocationsTable)
        db?.execSQL(createCategoriesTable)
        
        insertDefaultCategories(db)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // For development: destroy and recreate everything
        db?.execSQL("DROP TABLE IF EXISTS ${SavedLocation.TABLE_NAME}")
        db?.execSQL("DROP TABLE IF EXISTS ${Category.TABLE_NAME}")
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    private fun insertDefaultCategories(db: SQLiteDatabase?) {
        val defaults = listOf(
            Triple("Home", "@drawable/ic_home", colorToHex(ContextCompat.getColor(context, R.color.primary))),
            Triple("Favorites", "@drawable/ic_favorite", colorToHex(ContextCompat.getColor(context, R.color.primary_pink))),
            Triple("Others", "@drawable/ic_route", colorToHex(ContextCompat.getColor(context, R.color.text_secondary)))
        )

        defaults.forEach { (label, icon, color) ->
            val values = ContentValues().apply {
                put(Category.COLUMN_LABEL, label)
                put(Category.COLUMN_ICON, icon)
                put(Category.COLUMN_COLOR, color)
            }
            db?.insertWithOnConflict(Category.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    private fun colorToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }

    companion object {
        private const val DATABASE_NAME = "whereismycar_database"
        private const val DATABASE_VERSION = 1
    }
}
