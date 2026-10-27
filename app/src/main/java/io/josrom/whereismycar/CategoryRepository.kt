package io.josrom.whereismycar

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
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
                    categories.add(Category(id, label, icon, color, remoteIdOf(it)))
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
        // Read the API id before the row is gone
        val remoteId = getCategory(categoryId)?.remoteId
        val db = dbHelper.writableDatabase
        db.transaction {
            // A location always has a category: deleting the category deletes its locations
            // (the API does the same, so only the category delete is sent)
            delete(
                SavedLocation.TABLE_NAME,
                "${SavedLocation.COLUMN_CATEGORY_ID} = ?",
                arrayOf(categoryId.toString())
            )

            delete(
                Category.TABLE_NAME,
                "${Category.COLUMN_ID} = ?",
                arrayOf(categoryId.toString())
            )
        }

        // Auto-sync if enabled
        if (remoteId != null && shouldSyncCategory(categoryId)) {
            syncManager?.deleteCategoryFromCloud(remoteId)
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

                return Category(id, label, icon, color, remoteIdOf(it))
            }
            return null
        }
    }

    /**
     * The category used when a location is saved without one: "Others" if it exists, otherwise the first
     * category, otherwise a new "Others" category.
     */
    fun getOrCreateDefaultCategory(): Category {
        val categories = getAllCategories()
        categories.firstOrNull { it.label.equals(DBHelper.DEFAULT_CATEGORY_LABEL, ignoreCase = true) }?.let { return it }
        categories.firstOrNull()?.let { return it }

        val (label, icon, color) = dbHelper.defaultCategory()
        val id = insertCategory(Category(label = label, icon = icon, color = color))
        return getCategory(id) ?: Category(id, label, icon, color)
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

    // Sync with the API. These methods never trigger an upload themselves.

    fun setRemoteId(categoryId: Int, remoteId: Int?) {
        val values = ContentValues().apply {
            if (remoteId == null) putNull(Category.COLUMN_REMOTE_ID) else put(Category.COLUMN_REMOTE_ID, remoteId)
        }
        dbHelper.writableDatabase.update(Category.TABLE_NAME, values, "${Category.COLUMN_ID} = ?", arrayOf(categoryId.toString()))
    }

    fun clearAllRemoteIds() {
        val values = ContentValues().apply { putNull(Category.COLUMN_REMOTE_ID) }
        dbHelper.writableDatabase.update(Category.TABLE_NAME, values, null, null)
    }

    /** Inserts a category downloaded from the API, already linked. Returns the local id, or -1 on conflict. */
    fun insertFromRemote(category: Category): Int {
        val values = ContentValues().apply {
            put(Category.COLUMN_LABEL, category.label)
            put(Category.COLUMN_ICON, category.icon)
            put(Category.COLUMN_COLOR, category.color)
            put(Category.COLUMN_REMOTE_ID, category.remoteId)
        }
        return dbHelper.writableDatabase
            .insertWithOnConflict(Category.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE).toInt()
    }

    private fun remoteIdOf(cursor: Cursor): Int? {
        val index = cursor.getColumnIndex(Category.COLUMN_REMOTE_ID)
        return if (index < 0 || cursor.isNull(index)) null else cursor.getInt(index)
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
