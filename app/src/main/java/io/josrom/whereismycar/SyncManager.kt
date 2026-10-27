package io.josrom.whereismycar

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import io.josrom.whereismycar.api.ApiCategory
import io.josrom.whereismycar.api.ApiClient
import io.josrom.whereismycar.api.ApiMappers.differsFrom
import io.josrom.whereismycar.api.ApiMappers.toBody
import io.josrom.whereismycar.api.ApiMappers.toLocal
import io.josrom.whereismycar.api.ApiSavedLocation
import io.josrom.whereismycar.api.WhereIsMyCarApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import retrofit2.HttpException
import retrofit2.Response

/**
 * Syncs the local SQLite data with the Where Is My Car API.
 *
 * SQLite stays the source of truth, so the app works offline. Each local row keeps the id it has in
 * the API (`remoteId`). A full sync downloads what is missing locally and uploads local changes (local
 * wins on conflicts). Single changes (insert, update, delete) are pushed right away when auto sync is on.
 * All API calls run in background and one at a time.
 */
@Suppress("DEPRECATION")
class SyncManager(context: Context) {

    private val appContext = context.applicationContext

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(BuildConfig.GOOGLE_API_KEY)
        .requestEmail()
        .build()

    val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(appContext, gso)
    private val auth = FirebaseAuth.getInstance()
    private val api: WhereIsMyCarApi by lazy { ApiClient.default }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(appContext)
    private val categoryRepository get() = CategoryRepository(appContext)
    private val locationRepository get() = SavedLocationRepository(appContext)

    interface SyncStatsListener {
        fun onStatsLoaded(localLocations: Int, localCats: Int, cloudLocations: Int, cloudCats: Int)
    }

    fun getSyncStats(listener: SyncStatsListener) {
        scope.launch {
            val localLocations = locationRepository.getAllLocations().size
            val localCategories = categoryRepository.getAllCategories().size

            val (cloudLocations, cloudCategories) = if (auth.currentUser == null) {
                0 to 0
            } else {
                try {
                    api.savedLocations().size to api.categories().size
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load the cloud stats", e)
                    0 to 0
                }
            }

            withContext(Dispatchers.Main) {
                listener.onStatsLoaded(localLocations, localCategories, cloudLocations, cloudCategories)
            }
        }
    }

    fun sync() {
        val syncEnabled = prefs.getBoolean("sync_enabled", true)
        Log.d(TAG, "sync() called - syncEnabled=$syncEnabled")
        if (!syncEnabled) return

        val account = GoogleSignIn.getLastSignedInAccount(appContext) ?: return

        googleSignInClient.silentSignIn()
            .addOnSuccessListener { updatedAccount -> firebaseAuthWithGoogle(updatedAccount) }
            .addOnFailureListener { firebaseAuthWithGoogle(account) }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    scope.launch {
                        // sync() runs on every onResume: skip if a sync is already running
                        if (mutex.tryLock()) {
                            try {
                                runSafely("full sync") { fullSync() }
                            } finally {
                                mutex.unlock()
                            }
                        }
                    }
                }
            }
    }

    /** Deletes everything in the API and uploads the local data again. */
    fun forceUpload(onComplete: () -> Unit = {}) {
        if (auth.currentUser == null) return

        scope.launch {
            mutex.withLock {
                runSafely("force upload") {
                    api.me()
                    api.savedLocations().forEach { deleteIgnoringNotFound { api.deleteSavedLocation(it.id) } }
                    api.categories().forEach { deleteIgnoringNotFound { api.deleteCategory(it.id) } }
                    locationRepository.clearAllRemoteIds()
                    categoryRepository.clearAllRemoteIds()
                    fullSync()
                }
            }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    /** Deletes the local data and downloads everything from the API. */
    fun forceDownload(onComplete: () -> Unit = {}) {
        if (auth.currentUser == null) return

        scope.launch {
            mutex.withLock {
                runSafely("force download") {
                    // Check the API is reachable before deleting anything locally
                    api.me()
                    clearLocalData()
                    fullSync()
                }
            }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun uploadCategory(category: Category) {
        if (!prefs.getBoolean("sync_auto", true) || auth.currentUser == null) return

        scope.launch {
            mutex.withLock {
                runSafely("upload category ${category.id}") {
                    categoryRepository.getCategory(category.id)?.let { pushCategory(it) }
                }
            }
        }
    }

    fun uploadLocation(location: SavedLocation) {
        if (!prefs.getBoolean("sync_auto", true) || auth.currentUser == null) return

        scope.launch {
            mutex.withLock {
                runSafely("upload location ${location.id}") {
                    // Reload it: the caller may pass a copy without the remote id
                    val current = locationRepository.getLocation(location.id) ?: return@runSafely
                    if (isLocationSyncable(current.categoryId)) {
                        pushLocation(current)
                    } else if (current.remoteId != null) {
                        // Its category is no longer synced: remove it from the API
                        deleteIgnoringNotFound { api.deleteSavedLocation(current.remoteId) }
                        locationRepository.setRemoteId(current.id, null)
                    }
                }
            }
        }
    }

    /** [remoteId] is the id in the API, read before deleting the local row. */
    fun deleteFromCloud(remoteId: Int) {
        if (auth.currentUser == null) return

        scope.launch {
            mutex.withLock {
                runSafely("delete location $remoteId") { deleteIgnoringNotFound { api.deleteSavedLocation(remoteId) } }
            }
        }
    }

    /** [remoteId] is the id in the API. The API keeps the locations of the category, without category. */
    fun deleteCategoryFromCloud(remoteId: Int) {
        if (auth.currentUser == null) return

        scope.launch {
            mutex.withLock {
                runSafely("delete category $remoteId") { deleteIgnoringNotFound { api.deleteCategory(remoteId) } }
            }
        }
    }

    fun signOut() {
        auth.signOut()
        googleSignInClient.signOut()
    }

    fun getLastSyncTime(callback: (Long?) -> Unit) {
        val timestamp = prefs.getLong(LAST_SYNC_KEY, 0L)
        callback(if (timestamp > 0) timestamp else null)
    }

    // Full sync

    private suspend fun fullSync() {
        // Creates the user in the API on the first sync
        api.me()
        syncCategories()
        syncLocations()
        prefs.edit { putLong(LAST_SYNC_KEY, System.currentTimeMillis()) }
    }

    private suspend fun syncCategories() {
        val remote = api.categories().associateBy { it.id }.toMutableMap()
        val repository = categoryRepository

        for (local in repository.getAllCategories()) {
            val remoteId = local.remoteId ?: continue
            when {
                // No longer selected for sync: remove it from the API
                !isCategorySyncable(local.id) -> {
                    deleteIgnoringNotFound { api.deleteCategory(remoteId) }
                    repository.setRemoteId(local.id, null)
                    remote.remove(remoteId)
                }
                // Deleted in the API (or the API data was reset): upload it again below
                remoteId !in remote -> repository.setRemoteId(local.id, null)
            }
        }

        // Download what is missing locally. A local category with the same label is linked instead of
        // duplicated (the API does not allow two categories with the same label).
        val linkedRemoteIds = repository.getAllCategories().mapNotNull { it.remoteId }.toSet()
        for (remoteCategory in remote.values.filter { it.id !in linkedRemoteIds }) {
            val sameLabel = repository.getAllCategories()
                .firstOrNull { it.remoteId == null && it.label.equals(remoteCategory.label, ignoreCase = true) }

            if (sameLabel != null) {
                repository.setRemoteId(sameLabel.id, remoteCategory.id)
            } else {
                repository.insertFromRemote(remoteCategory.toLocal())
            }
        }

        // Upload local changes
        for (local in repository.getAllCategories().filter { isCategorySyncable(it.id) }) {
            pushCategory(local, remote[local.remoteId])
        }
    }

    private suspend fun syncLocations() {
        val remote = api.savedLocations().associateBy { it.id }.toMutableMap()
        val repository = locationRepository

        for (local in repository.getAllLocations()) {
            val remoteId = local.remoteId ?: continue
            when {
                !isLocationSyncable(local.categoryId) -> {
                    deleteIgnoringNotFound { api.deleteSavedLocation(remoteId) }
                    repository.setRemoteId(local.id, null)
                    remote.remove(remoteId)
                }
                remoteId !in remote -> repository.setRemoteId(local.id, null)
            }
        }

        val localCategoryIdByRemoteId = categoryRepository.getAllCategories()
            .mapNotNull { category -> category.remoteId?.let { it to category.id } }
            .toMap()

        val linkedRemoteIds = repository.getAllLocations().mapNotNull { it.remoteId }.toSet()
        for (remoteLocation in remote.values.filter { it.id !in linkedRemoteIds }) {
            repository.insertFromRemote(remoteLocation.toLocal(localCategoryIdByRemoteId[remoteLocation.categoryId]))
        }

        for (local in repository.getAllLocations().filter { isLocationSyncable(it.categoryId) }) {
            pushLocation(local, remote[local.remoteId])
        }
    }

    // Single uploads

    /**
     * Creates or updates the category in the API and returns its API id. [remote] is the current API copy,
     * used to skip the update when nothing changed.
     */
    private suspend fun pushCategory(local: Category, remote: ApiCategory? = null): Int? {
        val body = local.toBody()

        if (local.remoteId == null) {
            return try {
                api.createCategory(body).id.also { categoryRepository.setRemoteId(local.id, it) }
            } catch (e: HttpException) {
                if (e.code() != 409) throw e
                // Same label already in the API (created from another device): link to it
                api.categories().firstOrNull { it.label.equals(body.label, ignoreCase = true) }
                    ?.id?.also { categoryRepository.setRemoteId(local.id, it) }
            }
        }

        if (remote == null || body.differsFrom(remote)) {
            try {
                api.updateCategory(local.remoteId, body)
            } catch (e: HttpException) {
                when (e.code()) {
                    // Deleted in the API: the next full sync uploads it again
                    404 -> categoryRepository.setRemoteId(local.id, null)
                    // Renamed to a label that another category already has in the API
                    409 -> Log.w(TAG, "Category ${local.id} not updated: label already in use in the API")
                    else -> throw e
                }
            }
        }

        return local.remoteId
    }

    /** Creates or updates the location in the API. [remote] is the current API copy. */
    private suspend fun pushLocation(local: SavedLocation, remote: ApiSavedLocation? = null) {
        val remoteCategoryId = local.categoryId?.let { remoteCategoryIdFor(it) }
        val body = local.toBody(remoteCategoryId)

        if (local.remoteId == null) {
            api.createSavedLocation(body).id.also { locationRepository.setRemoteId(local.id, it) }
            return
        }

        if (remote == null || body.differsFrom(remote)) {
            try {
                api.updateSavedLocation(local.remoteId, body)
            } catch (e: HttpException) {
                if (e.code() != 404) throw e
                locationRepository.setRemoteId(local.id, null)
            }
        }
    }

    /** The API id of a local category, uploading the category first if it was never synced. */
    private suspend fun remoteCategoryIdFor(localCategoryId: Int): Int? {
        val category = categoryRepository.getCategory(localCategoryId) ?: return null
        return category.remoteId ?: pushCategory(category)
    }

    // Helpers

    private fun isCategorySyncable(categoryId: Int): Boolean =
        prefs.getBoolean("sync_all_categories", true) || selectedCategoryIds().contains(categoryId)

    /** Uncategorized locations use -1 in the selection. */
    private fun isLocationSyncable(categoryId: Int?): Boolean =
        prefs.getBoolean("sync_all_categories", true) || selectedCategoryIds().contains(categoryId ?: -1)

    private fun selectedCategoryIds(): List<Int> {
        val json = prefs.getString("sync_selected_categories", "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getInt(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun clearLocalData() {
        val db = DBHelper(appContext, null).writableDatabase
        db.delete(SavedLocation.TABLE_NAME, null, null)
        db.delete(Category.TABLE_NAME, null, null)
    }

    /** A 404 on delete means it is already gone, which is what we wanted. */
    private suspend fun deleteIgnoringNotFound(call: suspend () -> Response<Unit>) {
        val response = call()
        if (!response.isSuccessful && response.code() != 404) {
            throw HttpException(response)
        }
    }

    /** Sync failures (offline, API down, ...) are logged and retried on the next sync. */
    private suspend fun runSafely(operation: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Sync operation failed: $operation", e)
        }
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val LAST_SYNC_KEY = "last_sync_timestamp"
    }
}
