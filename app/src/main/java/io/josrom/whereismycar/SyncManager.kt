package io.josrom.whereismycar

import android.content.ContentValues
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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import org.json.JSONObject

@Suppress("DEPRECATION")
class SyncManager(private val context: Context) {

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(BuildConfig.GOOGLE_API_KEY)
        .requestEmail()
        .build()

    val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    interface SyncStatsListener {
        fun onStatsLoaded(localLocations: Int, localCats: Int, cloudLocations: Int, cloudCats: Int)
    }

    fun getSyncStats(listener: SyncStatsListener) {
        val locationRepository = SavedLocationRepository(context)
        val categoryRepository = CategoryRepository(context)
        
        val localLocs = locationRepository.getAllLocations().size
        val localCats = categoryRepository.getAllCategories().size
        
        val user = auth.currentUser
        if (user == null) {
            listener.onStatsLoaded(localLocs, localCats, 0, 0)
            return
        }

        database.getReference("users").child(user.uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cloudLocs = snapshot.child("locations").childrenCount.toInt()
                val cloudCats = snapshot.child("categories").childrenCount.toInt()
                listener.onStatsLoaded(localLocs, localCats, cloudLocs, cloudCats)
            }

            override fun onCancelled(error: DatabaseError) {
                listener.onStatsLoaded(localLocs, localCats, 0, 0)
            }
        })
    }

    fun sync() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        // General sync enabled check
        val syncEnabled = prefs.getBoolean("sync_enabled", true)
        
        Log.d("SyncManager", "sync() called - syncEnabled=$syncEnabled")

        if (syncEnabled) {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            
            if (account != null) {
                googleSignInClient.silentSignIn()
                    .addOnSuccessListener { updatedAccount ->
                        firebaseAuthWithGoogle(updatedAccount)
                    }
                    .addOnFailureListener { _ ->
                        firebaseAuthWithGoogle(account)
                    }
            }
        }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let { 
                        downloadAndMerge(it.uid)
                        uploadData(it.uid)
                        updateLastSyncTime()
                    }
                }
            }
    }

    private fun updateLastSyncTime() {
        val user = auth.currentUser ?: return
        val userRef = database.getReference("users").child(user.uid).child("lastSync")
        
        // Save server timestamp to Firebase
        val timestampMap = hashMapOf(
            "timestamp" to ServerValue.TIMESTAMP,
            "clientTime" to System.currentTimeMillis()
        )
        userRef.setValue(timestampMap)
        
        // Also save locally for immediate display
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit { putLong("last_sync_timestamp", System.currentTimeMillis()) }
    }

    fun uploadData(userId: String) {
        val locationRepository = SavedLocationRepository(context)
        val categoryRepository = CategoryRepository(context)
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val syncAll = prefs.getBoolean("sync_all_categories", true)
        val selectedCategoriesJson = prefs.getString("sync_selected_categories", "[]") ?: "[]"
        val selectedCategoryIds = try {
            val jsonArray = org.json.JSONArray(selectedCategoriesJson)
            List(jsonArray.length()) { jsonArray.getInt(it) }
        } catch (_: Exception) {
            emptyList()
        }

        val locations = locationRepository.getAllLocations()
        val categories = categoryRepository.getAllCategories()
        
        val categoriesToSync = mutableListOf<Int>()
        val locationsToSync = mutableListOf<Int>()

        val syncUncategorized = syncAll || selectedCategoryIds.contains(-1)

        for (category in categories) {
            if (syncAll || selectedCategoryIds.contains(category.id)) {
                uploadCategoryInternal(category)
                categoriesToSync.add(category.id)
            }
        }
        
        for (location in locations) {
            val shouldSync = syncAll || (location.categoryId != null && selectedCategoryIds.contains(location.categoryId)) || (location.categoryId == null && syncUncategorized)
            if (shouldSync) {
                uploadLocationInternal(location)
                locationsToSync.add(location.id)
            }
        }

        cleanupCloudData(userId, categoriesToSync, locationsToSync)
    }

    private fun cleanupCloudData(userId: String, validCategoryIds: List<Int>, validLocationIds: List<Int>) {
        val userRef = database.getReference("users").child(userId)
        
        userRef.child("categories").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (catSnapshot in snapshot.children) {
                    val id = catSnapshot.key?.toIntOrNull()
                    if (id != null && !validCategoryIds.contains(id)) {
                        catSnapshot.ref.removeValue()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        userRef.child("locations").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (locSnapshot in snapshot.children) {
                    val id = locSnapshot.key?.toIntOrNull()
                    if (id != null && !validLocationIds.contains(id)) {
                        locSnapshot.ref.removeValue()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun forceUpload(onComplete: () -> Unit = {}) {
        val user = auth.currentUser ?: return
        database.getReference("users").child(user.uid).removeValue()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    uploadData(user.uid)
                    updateLastSyncTime()
                    onComplete()
                }
            }
    }

    fun forceDownload(onComplete: () -> Unit = {}) {
        val user = auth.currentUser ?: return
        clearLocalData()
        downloadAndMerge(user.uid, onComplete)
        updateLastSyncTime()
    }

    private fun clearLocalData() {
        val dbHelper = DBHelper(context, null)
        val db = dbHelper.writableDatabase
        db.delete(SavedLocation.TABLE_NAME, null, null)
        db.delete(Category.TABLE_NAME, null, null)
    }

    fun uploadCategory(category: Category) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean("sync_auto", true)) return
        uploadCategoryInternal(category)
    }

    private fun uploadCategoryInternal(category: Category) {
        val user = auth.currentUser ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val syncAll = prefs.getBoolean("sync_all_categories", true)
        val selectedCategoriesJson = prefs.getString("sync_selected_categories", "[]") ?: "[]"
        val selectedCategoryIds = try {
            val jsonArray = org.json.JSONArray(selectedCategoriesJson)
            List(jsonArray.length()) { jsonArray.getInt(it) }
        } catch (_: Exception) {
            emptyList()
        }

        if (syncAll || selectedCategoryIds.contains(category.id)) {
            database.getReference("users").child(user.uid).child("categories")
                .child(category.id.toString()).setValue(category)
            updateLastSyncTime()
        }
    }

    fun uploadLocation(location: SavedLocation) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean("sync_auto", true)) return
        uploadLocationInternal(location)
    }

    private fun uploadLocationInternal(location: SavedLocation) {
        val user = auth.currentUser ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val syncAll = prefs.getBoolean("sync_all_categories", true)
        val selectedCategoriesJson = prefs.getString("sync_selected_categories", "[]") ?: "[]"
        val selectedCategoryIds = try {
            val jsonArray = org.json.JSONArray(selectedCategoriesJson)
            List(jsonArray.length()) { jsonArray.getInt(it) }
        } catch (_: Exception) {
            emptyList()
        }

        val syncUncategorized = syncAll || selectedCategoryIds.contains(-1)
        val shouldSync = syncAll || (location.categoryId != null && selectedCategoryIds.contains(location.categoryId)) || (location.categoryId == null && syncUncategorized)

        if (shouldSync) {
            val map = hashMapOf<String, Any?>(
                "id" to location.id,
                "displayName" to location.displayName,
                "address" to location.address,
                "city" to location.city,
                "country" to location.country,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "date" to location.date,
                "categoryId" to location.categoryId,
                "note" to location.note,
                "addressData" to location.addressData
            )
            database.getReference("users").child(user.uid).child("locations")
                .child(location.id.toString()).setValue(map)
            updateLastSyncTime()
        } else {
            database.getReference("users").child(user.uid).child("locations")
                .child(location.id.toString()).removeValue()
            updateLastSyncTime()
        }
    }

    private fun downloadAndMerge(userId: String, onComplete: (() -> Unit)? = null) {
        val userRef = database.getReference("users").child(userId)
        userRef.child("categories").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val categoryRepository = CategoryRepository(context)
                val localCategories = categoryRepository.getAllCategories()
                val localIds = localCategories.map { it.id }

                for (categorySnapshot in snapshot.children) {
                    val cloudCategory = categorySnapshot.getValue(Category::class.java)
                    if (cloudCategory != null && !localIds.contains(cloudCategory.id)) {
                        insertCloudCategory(cloudCategory)
                    }
                }
                syncLocations(userId, onComplete)
            }
            override fun onCancelled(error: DatabaseError) { onComplete?.invoke() }
        })
    }

    private fun syncLocations(userId: String, onComplete: (() -> Unit)? = null) {
        val userRef = database.getReference("users").child(userId).child("locations")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locationRepository = SavedLocationRepository(context)
                val localLocations = locationRepository.getAllLocations()
                val localIds = localLocations.map { it.id }

                for (locationSnapshot in snapshot.children) {
                    val id = locationSnapshot.child("id").getValue(Int::class.java) ?: 0
                    if (!localIds.contains(id)) {
                        val cloudLocation = parseLocation(locationSnapshot)
                        if (cloudLocation != null) {
                            insertCloudLocation(cloudLocation)
                        }
                    }
                }
                onComplete?.invoke()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun parseLocation(snapshot: DataSnapshot): SavedLocation? {
        return try {
            val id = snapshot.child("id").getValue(Int::class.java) ?: 0
            val displayName = snapshot.child("displayName").getValue(String::class.java) ?: ""
            val address = snapshot.child("address").getValue(String::class.java) ?: ""
            val city = snapshot.child("city").getValue(String::class.java) ?: ""
            val country = snapshot.child("country").getValue(String::class.java) ?: ""
            val latitude = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
            val longitude = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
            val date = snapshot.child("date").getValue(String::class.java) ?: ""
            val categoryId = snapshot.child("categoryId").getValue(Int::class.java)
            val note = snapshot.child("note").getValue(String::class.java)
            @Suppress("UNCHECKED_CAST")
            val addressData = snapshot.child("addressData").value as? HashMap<String, String> ?: hashMapOf()

            SavedLocation(id, displayName, address, city, country, latitude, longitude, date, categoryId, note, addressData)
        } catch (_: Exception) { null }
    }

    private fun insertCloudCategory(category: Category) {
        val dbHelper = DBHelper(context, null)
        val values = ContentValues().apply {
            put(Category.COLUMN_ID, category.id)
            put(Category.COLUMN_LABEL, category.label)
            put(Category.COLUMN_ICON, category.icon)
            put(Category.COLUMN_COLOR, category.color)
        }
        dbHelper.writableDatabase.insertWithOnConflict(Category.TABLE_NAME, null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun insertCloudLocation(location: SavedLocation) {
        val dbHelper = DBHelper(context, null)
        val values = ContentValues().apply {
            put(SavedLocation.COLUMN_ID, location.id)
            put(SavedLocation.COLUMN_DISPLAY_NAME, location.displayName)
            put(SavedLocation.COLUMN_ADDRESS, location.address)
            put(SavedLocation.COLUMN_CITY, location.city)
            put(SavedLocation.COLUMN_COUNTRY, location.country)
            put(SavedLocation.COLUMN_LATITUDE, location.latitude)
            put(SavedLocation.COLUMN_LONGITUDE, location.longitude)
            put(SavedLocation.COLUMN_DATE, location.date)
            put(SavedLocation.COLUMN_CATEGORY_ID, location.categoryId)
            put(SavedLocation.COLUMN_NOTE, location.note)
            put(SavedLocation.COLUMN_ADDRESS_DATA, JSONObject(location.addressData as Map<*, *>).toString())
        }
        dbHelper.writableDatabase.insertWithOnConflict(SavedLocation.TABLE_NAME, null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun deleteFromCloud(locationId: Int) {
        val user = auth.currentUser ?: return
        database.getReference("users").child(user.uid).child("locations").child(locationId.toString()).removeValue()
        updateLastSyncTime()
    }

    fun deleteCategoryFromCloud(categoryId: Int) {
        val user = auth.currentUser ?: return
        database.getReference("users").child(user.uid).child("categories").child(categoryId.toString()).removeValue()
        updateLastSyncTime()
    }

    fun signOut() {
        auth.signOut()
        googleSignInClient.signOut()
    }

    fun getLastSyncTime(callback: (Long?) -> Unit) {
        val user = auth.currentUser ?: run {
            callback(null)
            return
        }
        
        val userRef = database.getReference("users").child(user.uid).child("lastSync")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val serverTimestamp = snapshot.child("timestamp").getValue(Long::class.java)
                callback(serverTimestamp)
            }
            
            override fun onCancelled(error: DatabaseError) {
                // Fallback to local timestamp if Firebase fails
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val localTimestamp = prefs.getLong("last_sync_timestamp", 0L)
                callback(if (localTimestamp > 0) localTimestamp else null)
            }
        })
    }
}
