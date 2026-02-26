package io.josrom.whereismycar

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DBHelper(context: Context, factory: SQLiteDatabase.CursorFactory?) : SQLiteOpenHelper(context, DATABASE_NAME, factory, DATABASE_VERSION) {
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
                ${Category.COLUMN_LABEL} TEXT,
                ${Category.COLUMN_ICON} TEXT
            )
        """.trimIndent()

        db?.execSQL(createLocationsTable)
        db?.execSQL(createCategoriesTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db?.execSQL("ALTER TABLE ${SavedLocation.TABLE_NAME} ADD COLUMN ${SavedLocation.COLUMN_CATEGORY_ID} INTEGER")
            db?.execSQL("ALTER TABLE ${SavedLocation.TABLE_NAME} ADD COLUMN ${SavedLocation.COLUMN_NOTE} TEXT")
            
            val createCategoriesTable = """
                CREATE TABLE ${Category.TABLE_NAME} (
                    ${Category.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${Category.COLUMN_LABEL} TEXT,
                    ${Category.COLUMN_ICON} TEXT
                )
            """.trimIndent()
            db?.execSQL(createCategoriesTable)
        }

        if (oldVersion < 3) {
            db?.execSQL("ALTER TABLE ${SavedLocation.TABLE_NAME} DROP COLUMN ${SavedLocation.COLUMN_ADDRESS}")
            db?.execSQL("ALTER TABLE ${SavedLocation.TABLE_NAME} ADD COLUMN ${SavedLocation.COLUMN_ADDRESS} TEXT")
            db?.execSQL("ALTER TABLE ${SavedLocation.TABLE_NAME} ADD COLUMN ${SavedLocation.COLUMN_ADDRESS_DATA} JSON")
        }
    }

    companion object {
        private const val DATABASE_NAME = "whereismycar_database"
        private const val DATABASE_VERSION = 3
    }
}
