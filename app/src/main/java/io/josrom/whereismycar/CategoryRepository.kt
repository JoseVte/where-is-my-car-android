package io.josrom.whereismycar

import android.content.ContentValues
import android.content.Context
import androidx.core.database.sqlite.transaction
import androidx.preference.PreferenceManager
import org.json.JSONArray

class CategoryRepository(private val context: Context) {
    private val dbHelper = DBHelper(context, null)
    private var syncManager: SyncManager? = null

    fun setSyncManager(syncManager: SyncManager) {
        this.syncManager = syncManager
    }

    fun getAllCategories(): MutableList<Category> {
        val categories: MutableList<Category> = mutableListOf()
        val queryString = "SELECT * FROM ${Category.TABLE_NAME}"

        dbHelper.readableDatabase.rawQuery(queryString, null).use {
            if (it.moveToFirst()) {
                do {
                    val id = it.getInt(it.getColumnIndexOrThrow(Category.COLUMN_ID))
                    val label = it.getString(it.getColumnIndexOrThrow(Category.COLUMN_LABEL))
                    val icon = it.getString(it.getColumnIndexOrThrow(Category.COLUMN_ICON))
                    val color = it.getString(it.getColumnIndexOrThrow(Category.COLUMN_COLOR))
                    categories.add(Category(id, label, icon, color))
                } while (it.moveToNext())
            }
        }
        return categories
    }

    fun insertCategory(category: Category): Int {
        val values = ContentValues().apply {
            put(Category.COLUMN_LABEL, category.label)
            put(Category.COLUMN_ICON, category.icon)
            put(Category.COLUMN_COLOR, category.color)
        }
        val newId = dbHelper.writableDatabase.insert(Category.TABLE_NAME, null, values).toInt()

        // Auto-sync if enabled
        if (shouldSyncCategory(newId)) {
            val categoryWithId = Category(newId, category.label, category.icon, category.color)
            syncManager?.uploadCategory(categoryWithId)
        }

        return newId
    }

    fun updateCategory(category: Category) {
        val values = ContentValues().apply {
            put(Category.COLUMN_LABEL, category.label)
            put(Category.COLUMN_ICON, category.icon)
            put(Category.COLUMN_COLOR, category.color)
        }
        dbHelper.writableDatabase.update(
            Category.TABLE_NAME,
            values,
            "${Category.COLUMN_ID} = ?",
            arrayOf(category.id.toString())
        )

        // Auto-sync if enabled
        if (shouldSyncCategory(category.id)) {
            syncManager?.uploadCategory(category)
        }
    }

    fun deleteCategory(categoryId: Int) {
        val db = dbHelper.writableDatabase
        db.transaction {
            try {
                // Unset category_id from locations
                val values = ContentValues().apply {
                    putNull(SavedLocation.COLUMN_CATEGORY_ID)
                }
                update(
                    SavedLocation.TABLE_NAME,
                    values,
                    "${SavedLocation.COLUMN_CATEGORY_ID} = ?",
                    arrayOf(categoryId.toString())
                )

                // Delete the category
                delete(
                    Category.TABLE_NAME,
                    "${Category.COLUMN_ID} = ?",
                    arrayOf(categoryId.toString())
                )
            } finally {
            }
        }

        // Auto-sync if enabled
        if (shouldSyncCategory(categoryId)) {
            syncManager?.deleteCategoryFromCloud(categoryId)
        }
    }

    fun getCategory(categoryId: Int): Category? {
        val queryString =
            "SELECT * FROM ${Category.TABLE_NAME} WHERE ${Category.COLUMN_ID} = $categoryId"

        dbHelper.readableDatabase.rawQuery(queryString, null).use {
            if (it.moveToFirst()) {
                val id = it.getInt(it.getColumnIndexOrThrow(Category.COLUMN_ID))
                val label = it.getString(it.getColumnIndexOrThrow(Category.COLUMN_LABEL))
                val icon = it.getString(it.getColumnIndexOrThrow(Category.COLUMN_ICON))
                val color = it.getString(it.getColumnIndexOrThrow(Category.COLUMN_COLOR))

                return Category(id, label, icon, color)
            }
            return null
        }
    }

    fun getLocationCountForCategory(categoryId: Int): Int {
        val queryString = "SELECT COUNT(*) FROM ${SavedLocation.TABLE_NAME} WHERE ${SavedLocation.COLUMN_CATEGORY_ID} = $categoryId"
        dbHelper.readableDatabase.rawQuery(queryString, null).use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    private fun shouldSyncCategory(categoryId: Int): Boolean {
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
            selectedIds.contains(categoryId)
        } catch (_: Exception) {
            false
        }
    }
}
