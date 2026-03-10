package io.josrom.whereismycar

import android.content.ContentValues
import android.content.Context

class CategoryRepository(context: Context) {
    private val dbHelper = DBHelper(context, null)

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

    fun insertCategory(category: Category) {
        val values = ContentValues().apply {
            put(Category.COLUMN_LABEL, category.label)
            put(Category.COLUMN_ICON, category.icon)
            put(Category.COLUMN_COLOR, category.color)
        }
        dbHelper.writableDatabase.insert(Category.TABLE_NAME, null, values)
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
    }

    fun deleteCategory(categoryId: Int) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Unset category_id from locations
            val values = ContentValues().apply {
                putNull(SavedLocation.COLUMN_CATEGORY_ID)
            }
            db.update(
                SavedLocation.TABLE_NAME,
                values,
                "${SavedLocation.COLUMN_CATEGORY_ID} = ?",
                arrayOf(categoryId.toString())
            )

            // Delete the category
            db.delete(
                Category.TABLE_NAME,
                "${Category.COLUMN_ID} = ?",
                arrayOf(categoryId.toString())
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
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
}
