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
                ${SavedLocation.COLUMN_ADDRESS_DATA} JSON,
                ${SavedLocation.COLUMN_REMOTE_ID} INTEGER
            )
        """.trimIndent()

        val createCategoriesTable = """
            CREATE TABLE ${Category.TABLE_NAME} (
                ${Category.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Category.COLUMN_LABEL} TEXT UNIQUE,
                ${Category.COLUMN_ICON} TEXT,
                ${Category.COLUMN_COLOR} TEXT,
                ${Category.COLUMN_REMOTE_ID} INTEGER
            )
        """.trimIndent()

        db?.execSQL(createLocationsTable)
        db?.execSQL(createCategoriesTable)
        
        insertDefaultCategories(db)
    }

    /** Migrates step by step, keeping the user's data. */
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Version 2: link with the API (sync with Firebase Realtime Database is gone)
            db?.execSQL("ALTER TABLE ${SavedLocation.TABLE_NAME} ADD COLUMN ${SavedLocation.COLUMN_REMOTE_ID} INTEGER")
            db?.execSQL("ALTER TABLE ${Category.TABLE_NAME} ADD COLUMN ${Category.COLUMN_REMOTE_ID} INTEGER")
        }
        if (oldVersion < 4) {
            // Version 4: the default categories store a string resource as label, so they are translated.
            // Runs before the version 3 step, which creates the default category with the new label.
            db?.let { database ->
                mapOf(
                    "Home" to HOME_CATEGORY_LABEL,
                    "Favorites" to FAVORITES_CATEGORY_LABEL,
                    "Others" to DEFAULT_CATEGORY_LABEL
                ).forEach { (old, new) ->
                    database.execSQL(
                        "UPDATE OR IGNORE ${Category.TABLE_NAME} SET ${Category.COLUMN_LABEL} = ? WHERE ${Category.COLUMN_LABEL} = ? COLLATE NOCASE",
                        arrayOf(new, old)
                    )
                }
            }
        }
        if (oldVersion < 3) {
            // Version 3: every location has a category. Locations without one (or pointing to a deleted
            // category) move to the default category, created if needed.
            db?.let { database ->
                insertDefaultCategory(database)
                database.execSQL(
                    """
                    UPDATE ${SavedLocation.TABLE_NAME}
                    SET ${SavedLocation.COLUMN_CATEGORY_ID} = (
                        SELECT ${Category.COLUMN_ID} FROM ${Category.TABLE_NAME}
                        WHERE ${Category.COLUMN_LABEL} = ? COLLATE NOCASE LIMIT 1
                    )
                    WHERE ${SavedLocation.COLUMN_CATEGORY_ID} IS NULL
                       OR ${SavedLocation.COLUMN_CATEGORY_ID} NOT IN (SELECT ${Category.COLUMN_ID} FROM ${Category.TABLE_NAME})
                    """.trimIndent(),
                    arrayOf(DEFAULT_CATEGORY_LABEL)
                )
            }
        }
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Only happens when installing an older build during development: destroy and recreate everything
        db?.execSQL("DROP TABLE IF EXISTS ${SavedLocation.TABLE_NAME}")
        db?.execSQL("DROP TABLE IF EXISTS ${Category.TABLE_NAME}")
        onCreate(db)
    }

    private fun insertDefaultCategories(db: SQLiteDatabase?) {
        val defaults = listOf(
            Triple(HOME_CATEGORY_LABEL, "@drawable/ic_home", colorToHex(ContextCompat.getColor(context, R.color.primary))),
            Triple(FAVORITES_CATEGORY_LABEL, "@drawable/ic_favorite", colorToHex(ContextCompat.getColor(context, R.color.primary_pink))),
            defaultCategory()
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

    /** Label, icon and color of the category used when a location is saved without one. */
    fun defaultCategory() = Triple(
        DEFAULT_CATEGORY_LABEL,
        "@drawable/ic_route",
        colorToHex(ContextCompat.getColor(context, R.color.text_secondary))
    )

    private fun insertDefaultCategory(db: SQLiteDatabase) {
        val (label, icon, color) = defaultCategory()
        val exists = db.rawQuery(
            "SELECT 1 FROM ${Category.TABLE_NAME} WHERE ${Category.COLUMN_LABEL} = ? COLLATE NOCASE",
            arrayOf(label)
        ).use { it.moveToFirst() }

        if (!exists) {
            val values = ContentValues().apply {
                put(Category.COLUMN_LABEL, label)
                put(Category.COLUMN_ICON, icon)
                put(Category.COLUMN_COLOR, color)
            }
            db.insert(Category.TABLE_NAME, null, values)
        }
    }

    private fun colorToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }

    companion object {
        private const val DATABASE_NAME = "whereismycar_database"
        private const val DATABASE_VERSION = 4
        const val HOME_CATEGORY_LABEL = "@string/category_home"
        const val FAVORITES_CATEGORY_LABEL = "@string/category_favorites"
        const val DEFAULT_CATEGORY_LABEL = "@string/category_others"
    }
}
