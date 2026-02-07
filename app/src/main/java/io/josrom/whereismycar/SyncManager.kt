package io.josrom.whereismycar

import android.content.ContentValues
import android.content.Context
import android.util.Log
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
import com.google.firebase.database.ValueEventListener
import org.json.JSONObject

class SyncManager(private val context: Context) {

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()

    val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    fun sync() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val syncEnabled = prefs.getBoolean("sync", false)

        if (syncEnabled) {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                firebaseAuthWithGoogle(account)
            } else {
                Log.d("SyncManager", "User not signed in, skipping sync")
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
                    }
                } else {
                    Log.w("SyncManager", "Firebase auth failed", task.exception)
                }
            }
    }

    private fun uploadData(userId: String) {
        val dbHelper = DBHelper(context, null)
        val locations = dbHelper.getAllLocations()
        val userRef = database.getReference("users").child(userId).child("locations")
        
        for (location in locations) {
            userRef.child(location.id.toString()).setValue(location)
        }
    }

    private fun downloadAndMerge(userId: String) {
        val userRef = database.getReference("users").child(userId).child("locations")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dbHelper = DBHelper(context, null)
                val localLocations = dbHelper.getAllLocations()
                val localIds = localLocations.map { it.id }

                for (locationSnapshot in snapshot.children) {
                    val cloudLocation = locationSnapshot.getValue(SavedLocation::class.java)
                    if (cloudLocation != null && !localIds.contains(cloudLocation.id)) {
                        insertCloudLocation(dbHelper, cloudLocation)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SyncManager", "Failed to download data", error.toException())
            }
        })
    }

    private fun insertCloudLocation(dbHelper: DBHelper, location: SavedLocation) {
        val values = ContentValues().apply {
            put(DBHelper.COLUMN_ID, location.id)
            put(DBHelper.COLUMN_ADDRESS, JSONObject(location.address as Map<*, *>).toString())
            put(DBHelper.COLUMN_DISPLAY_NAME, location.displayName)
            put(DBHelper.COLUMN_CITY, location.city)
            put(DBHelper.COLUMN_COUNTRY, location.country)
            put(DBHelper.COLUMN_LATITUDE, location.latitude)
            put(DBHelper.COLUMN_LONGITUDE, location.longitude)
            put(DBHelper.COLUMN_DATE, location.date)
        }
        dbHelper.writableDatabase.insertWithOnConflict(DBHelper.TABLE_NAME, null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun deleteFromCloud(locationId: Int) {
        val user = auth.currentUser ?: return
        database.getReference("users")
            .child(user.uid)
            .child("locations")
            .child(locationId.toString())
            .removeValue()
    }
}