package io.josrom.whereismycar.api

import io.josrom.whereismycar.Category
import io.josrom.whereismycar.SavedLocation
import io.josrom.whereismycar.api.ApiMappers.differsFrom
import io.josrom.whereismycar.api.ApiMappers.toBody
import io.josrom.whereismycar.api.ApiMappers.toLocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ApiMappersTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun localDateIsConvertedToUtcUsingTheDeviceTimeZone() {
        // 20 October 2026 is summer time in Madrid (UTC+2)
        assertEquals("2026-10-20T09:30:00Z", ApiMappers.toApiDate("2026-10-20 11:30:00", madrid))
    }

    @Test
    fun invalidOrEmptyLocalDatesAreSentAsNull() {
        assertNull(ApiMappers.toApiDate("not a date", madrid))
        assertNull(ApiMappers.toApiDate("", madrid))
        assertNull(ApiMappers.toApiDate(null, madrid))
    }

    @Test
    fun apiDateIsConvertedToTheLocalFormat() {
        assertEquals("2026-10-20 11:30:00", ApiMappers.toLocalDate("2026-10-20T09:30:00Z", madrid))
        assertEquals("2026-10-20 11:30:00", ApiMappers.toLocalDate("2026-10-20T09:30:00.993Z", madrid))
    }

    @Test
    fun missingOrInvalidApiDateUsesNow() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        assertEquals("2026-01-01 01:00:00", ApiMappers.toLocalDate(null, madrid, now))
        assertEquals("2026-01-01 01:00:00", ApiMappers.toLocalDate("garbage", madrid, now))
    }

    @Test
    fun datesSurviveARoundTrip() {
        val local = "2026-03-29 03:15:00"
        assertEquals(local, ApiMappers.toLocalDate(ApiMappers.toApiDate(local, madrid), madrid))
    }

    @Test
    fun colorsAreNormalizedToRgb() {
        assertEquals("#E65100", ApiMappers.toApiColor("#E65100"))
        assertEquals("#E65100", ApiMappers.toApiColor("#FFE65100"))
        assertEquals("#607D8B", ApiMappers.toApiColor("red"))
        assertEquals("#607D8B", ApiMappers.toApiColor(""))
    }

    @Test
    fun categoryBodyRespectsTheApiLimits() {
        val body = Category(label = "  " + "a".repeat(60) + " ", icon = "@drawable/ic_home", color = "#80112233").toBody()

        assertEquals(50, body.label.length)
        assertEquals("@drawable/ic_home", body.icon)
        assertEquals("#112233", body.color)
    }

    @Test
    fun locationBodyUsesFallbacksForTheName() {
        val withAddress = SavedLocation(displayName = " ", address = "Gran Via 28", latitude = 40.4, longitude = -3.7)
        assertEquals("Gran Via 28", withAddress.toBody(null, madrid).displayName)

        val withNothing = SavedLocation(displayName = "", latitude = 40.123456, longitude = -3.7)
        assertEquals("40.12346, -3.70000", withNothing.toBody(null, madrid).displayName)
    }

    @Test
    fun locationBodyTruncatesAndOmitsEmptyValues() {
        val location = SavedLocation(
            displayName = "Spot", address = "x".repeat(300), city = "", country = "Spain",
            latitude = 1.0, longitude = 2.0, date = "2026-10-20 11:30:00", note = "n".repeat(600)
        )

        val body = location.toBody(remoteCategoryId = 7, zone = madrid)

        assertEquals(255, body.address!!.length)
        assertEquals(500, body.note!!.length)
        assertNull(body.city)
        assertNull(body.addressData)
        assertEquals("Spain", body.country)
        assertEquals("2026-10-20T09:30:00Z", body.date)
        assertEquals(7, body.categoryId)
    }

    @Test
    fun apiModelsAreConvertedToLinkedLocalModels() {
        val category = ApiCategory(id = 3, label = "Home", icon = "@drawable/ic_home", color = "#E65100").toLocal()
        assertEquals(3, category.remoteId)
        assertEquals("Home", category.label)

        val location = ApiSavedLocation(
            id = 9, displayName = "Garage", latitude = 1.0, longitude = 2.0, country = null, city = "Madrid",
            address = null, addressData = mapOf("postalCode" to "28013"), date = "2026-10-20T09:30:00Z",
            note = null, categoryId = 3
        ).toLocal(localCategoryId = 42, zone = madrid)

        assertEquals(9, location.remoteId)
        assertEquals(42, location.categoryId)
        assertEquals("", location.address)
        assertEquals("2026-10-20 11:30:00", location.date)
        assertEquals("28013", location.addressData["postalCode"])
    }

    @Test
    fun differsFromDetectsChangesOnly() {
        val remote = ApiCategory(id = 1, label = "Home", icon = "home", color = "#e65100")
        assertFalse(CategoryBody("Home", "home", "#E65100").differsFrom(remote))
        assertTrue(CategoryBody("House", "home", "#E65100").differsFrom(remote))

        val remoteLocation = ApiSavedLocation(1, "Spot", 1.0, 2.0, null, null, null, null, "2026-10-20T09:30:00.000Z", null, null)
        val same = SavedLocationBody("Spot", 1.0, 2.0, null, null, null, null, "2026-10-20T09:30:00Z", null, null)
        assertFalse(same.differsFrom(remoteLocation))
        assertTrue(same.copy(note = "new").differsFrom(remoteLocation))
        assertTrue(same.copy(categoryId = 5).differsFrom(remoteLocation))
    }
}
