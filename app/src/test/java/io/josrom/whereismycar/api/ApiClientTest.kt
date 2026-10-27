package io.josrom.whereismycar.api

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class ApiClientTest {
    private lateinit var server: MockWebServer
    private val requestedTokens = mutableListOf<Boolean>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(token: String? = "cached-token", refreshed: String? = "fresh-token") = ApiClient.create(
        baseUrl = server.url("/").toString(),
        tokenProvider = { forceRefresh ->
            requestedTokens.add(forceRefresh)
            if (forceRefresh) refreshed else token
        },
        languageProvider = { "es-ES" }
    )

    @Test
    fun sendsTheTokenAndTheLanguage() = runTest {
        server.enqueue(MockResponse().setBody("""[{"id": 1, "label": "Home", "icon": "home", "color": "#E65100"}]"""))

        val categories = api().categories()

        val request = server.takeRequest()
        assertEquals("/api/categories", request.path)
        assertEquals("Bearer cached-token", request.getHeader("Authorization"))
        assertEquals("es-ES", request.getHeader("Accept-Language"))
        assertEquals(listOf(ApiCategory(1, "Home", "home", "#E65100")), categories)
    }

    @Test
    fun sendsNoAuthorizationWhenNobodyIsSignedIn() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        try {
            api(token = null, refreshed = null).me()
        } catch (_: HttpException) {
        }

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun refreshesTheTokenOnceAfterA401() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("""{"uuid": "abc", "email": "a@b.c", "displayName": null}"""))

        val user = api().me()

        assertEquals("abc", user.uuid)
        assertEquals("Bearer cached-token", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer fresh-token", server.takeRequest().getHeader("Authorization"))
        assertEquals(listOf(false, true), requestedTokens)
    }

    @Test
    fun givesUpAfterTheRefreshedTokenIsAlsoRejected() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        val error = runCatching { api().me() }.exceptionOrNull()

        assertTrue(error is HttpException)
        assertEquals(401, (error as HttpException).code())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun parsesSavedLocationsWithOptionalFields() = runTest {
        server.enqueue(MockResponse().setBody("""
            [{"id": 5, "displayName": "Garage", "latitude": 40.4, "longitude": -3.7,
              "addressData": {"postalCode": "28013"}, "date": "2026-10-20T09:30:00Z", "categoryId": null}]
        """.trimIndent()))

        val location = api().savedLocations().single()

        assertEquals(5, location.id)
        assertNull(location.city)
        assertNull(location.categoryId)
        assertEquals("28013", location.addressData?.get("postalCode"))
    }

    @Test
    fun deleteReturnsTheStatusWithoutThrowing() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(204, api().deleteSavedLocation(1).code())
        assertEquals(404, api().deleteSavedLocation(2).code())
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun sendsTheBodyAsJson() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id": 9, "label": "Beach", "icon": "x", "color": "#000000"}"""))

        api().createCategory(CategoryBody("Beach", "x", "#000000"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("""{"label":"Beach","icon":"x","color":"#000000"}""", request.body.readUtf8())
    }
}
