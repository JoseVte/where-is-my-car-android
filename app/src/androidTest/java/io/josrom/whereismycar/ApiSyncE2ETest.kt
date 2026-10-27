package io.josrom.whereismycar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import io.josrom.whereismycar.api.ApiClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End to end test against a running API (BuildConfig.API_BASE_URL), signed in with a Firebase
 * email/password user. It does not delete anything that already exists in the API.
 *
 * Skipped unless credentials are passed, for example the seeder user of the API project:
 * ./gradlew connectedMobileDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=io.josrom.whereismycar.ApiSyncE2ETest \
 *   -Pandroid.testInstrumentationRunnerArguments.e2eEmail=seed@example.com \
 *   -Pandroid.testInstrumentationRunnerArguments.e2ePassword=...
 */
@RunWith(AndroidJUnit4::class)
class ApiSyncE2ETest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val auth = FirebaseAuth.getInstance()

    @Before
    fun signIn() {
        val args = InstrumentationRegistry.getArguments()
        val email = args.getString("e2eEmail")
        val password = args.getString("e2ePassword")
        assumeTrue("Pass e2eEmail and e2ePassword to run this test", !email.isNullOrBlank() && !password.isNullOrBlank())

        Tasks.await(auth.signInWithEmailAndPassword(email!!, password!!), 30, TimeUnit.SECONDS)
    }

    @After
    fun signOut() {
        auth.signOut()
    }

    @Test
    fun downloadsEverythingAndPushesSingleChanges() {
        val syncManager = SyncManager(context)
        val categories = CategoryRepository(context).apply { setSyncManager(syncManager) }
        val locations = SavedLocationRepository(context).apply { setSyncManager(syncManager) }
        val api = ApiClient.default

        // 1. Full download: local data becomes a linked copy of the API
        val downloaded = CountDownLatch(1)
        syncManager.forceDownload { downloaded.countDown() }
        assertTrue("forceDownload did not finish", downloaded.await(60, TimeUnit.SECONDS))

        val remoteCategories = runBlocking { api.categories() }
        val remoteLocations = runBlocking { api.savedLocations() }
        assertEquals(remoteCategories.map { it.id }.toSet(), categories.getAllCategories().mapNotNull { it.remoteId }.toSet())
        assertEquals(remoteLocations.map { it.id }.toSet(), locations.getAllLocations().mapNotNull { it.remoteId }.toSet())
        assertTrue("Every local row must be linked", locations.getAllLocations().all { it.remoteId != null })

        // Categories of downloaded locations point to the local copy of the right category
        val localCategoryByRemoteId = categories.getAllCategories().associateBy { it.remoteId }
        for (remote in remoteLocations.filter { it.categoryId != null }) {
            val local = locations.getAllLocations().single { it.remoteId == remote.id }
            assertEquals(localCategoryByRemoteId[remote.categoryId]?.id, local.categoryId)
        }

        // 2. A new local location is uploaded right away
        val category = categories.getAllCategories().first()
        val localId = locations.insertLocation(
            "Calle E2E 1", "E2E test spot", "Madrid", "Spain", 40.4, -3.7, hashMapOf("postalCode" to "28001"),
            category.id, "created by ApiSyncE2ETest"
        )
        val remoteId = waitFor("the location to get a remote id") { locations.getLocation(localId)?.remoteId }

        val uploaded = runBlocking { api.savedLocations() }.single { it.id == remoteId }
        assertEquals("E2E test spot", uploaded.displayName)
        assertEquals(category.remoteId, uploaded.categoryId)
        assertNotNull(uploaded.date)

        // 3. Deleting it locally deletes it in the API
        locations.deleteLocation(localId)
        waitFor("the location to be deleted in the API") {
            if (runBlocking { api.savedLocations() }.none { it.id == remoteId }) true else null
        }
    }

    private fun <T> waitFor(what: String, timeoutSeconds: Long = 30, check: () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        while (System.currentTimeMillis() < deadline) {
            check()?.let { return it }
            Thread.sleep(500)
        }
        throw AssertionError("Timed out waiting for $what")
    }
}
