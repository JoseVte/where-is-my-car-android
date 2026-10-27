package io.josrom.whereismycar.api

import io.josrom.whereismycar.Category
import io.josrom.whereismycar.SavedLocation
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Conversions between the local SQLite models and the API. The API enforces limits (lengths, color
 * format, required name) that local data does not always respect, so values are adapted here.
 */
object ApiMappers {
    /** Format of `date` in SQLite, in the device time zone. */
    private val LOCAL_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    private val RGB_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
    private val ARGB_COLOR = Regex("^#[0-9A-Fa-f]{8}$")
    private const val FALLBACK_COLOR = "#607D8B"

    /** Local date ("yyyy-MM-dd HH:mm:ss", device time zone) to ISO 8601 UTC, or null if it cannot be parsed. */
    fun toApiDate(localDate: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (localDate.isNullOrBlank()) return null

        return try {
            LocalDateTime.parse(localDate, LOCAL_DATE_FORMAT).atZone(zone).toInstant().toString()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** ISO 8601 date from the API to the local format. Uses the current time if it is missing or invalid. */
    fun toLocalDate(apiDate: String?, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): String {
        val instant = try {
            apiDate?.let { Instant.parse(it) } ?: now
        } catch (_: DateTimeParseException) {
            now
        }

        return instant.atZone(zone).format(LOCAL_DATE_FORMAT)
    }

    /** The color picker returns #AARRGGBB, the API only accepts #RRGGBB. */
    fun toApiColor(color: String): String = when {
        RGB_COLOR.matches(color) -> color
        ARGB_COLOR.matches(color) -> "#" + color.substring(3)
        else -> FALLBACK_COLOR
    }

    fun Category.toBody() = CategoryBody(
        label = label.trim().take(50),
        icon = icon.take(50),
        color = toApiColor(color)
    )

    fun SavedLocation.toBody(remoteCategoryId: Int?, zone: ZoneId = ZoneId.systemDefault()) = SavedLocationBody(
        displayName = displayName.trim().ifBlank { address.trim() }
            .ifBlank { String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude) }
            .take(100),
        latitude = latitude,
        longitude = longitude,
        country = country.takeIf { it.isNotBlank() }?.take(100),
        city = city.takeIf { it.isNotBlank() }?.take(100),
        address = address.takeIf { it.isNotBlank() }?.take(255),
        addressData = addressData.takeIf { it.isNotEmpty() },
        date = toApiDate(date, zone),
        note = note?.take(500),
        categoryId = remoteCategoryId
    )

    fun ApiCategory.toLocal() = Category(label = label, icon = icon, color = color, remoteId = id)

    fun ApiSavedLocation.toLocal(localCategoryId: Int?, zone: ZoneId = ZoneId.systemDefault()) = SavedLocation(
        displayName = displayName,
        address = address.orEmpty(),
        city = city.orEmpty(),
        country = country.orEmpty(),
        latitude = latitude,
        longitude = longitude,
        date = toLocalDate(date, zone),
        categoryId = localCategoryId,
        note = note,
        addressData = HashMap(addressData.orEmpty()),
        remoteId = id
    )

    /** True if the API copy differs from what we would send, so an update is needed. */
    fun CategoryBody.differsFrom(remote: ApiCategory) =
        label != remote.label || icon != remote.icon || !color.equals(remote.color, ignoreCase = true)

    fun SavedLocationBody.differsFrom(remote: ApiSavedLocation): Boolean =
        displayName != remote.displayName ||
            latitude != remote.latitude ||
            longitude != remote.longitude ||
            country != remote.country ||
            city != remote.city ||
            address != remote.address ||
            (addressData ?: emptyMap<String, String>()) != (remote.addressData ?: emptyMap<String, String>()) ||
            !sameInstant(date, remote.date) ||
            note != remote.note ||
            categoryId != remote.categoryId

    private fun sameInstant(a: String?, b: String?): Boolean {
        if (a == null || b == null) return a == b
        return try {
            Instant.parse(a) == Instant.parse(b)
        } catch (_: DateTimeParseException) {
            a == b
        }
    }
}
