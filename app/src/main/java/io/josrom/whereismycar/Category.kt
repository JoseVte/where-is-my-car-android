package io.josrom.whereismycar

data class Category(
    val id: Int = 0,
    val label: String = "",
    val icon: String = "", // Almacenará el nombre del recurso o un identificador de icono
    val color: String = "" // Color en formato hexadecimal o en formato de cadena
) {
    companion object {
        const val TABLE_NAME = "categories"
        const val COLUMN_ID = "id"
        const val COLUMN_LABEL = "label"
        const val COLUMN_ICON = "icon"
        const val COLUMN_COLOR = "color"
    }
}
