package io.josrom.whereismycar.api

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import io.josrom.whereismycar.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"

    /** Builds the API client. Kept separate from [default] so tests can point it to a fake server. */
    fun create(
        baseUrl: String,
        tokenProvider: TokenProvider,
        languageProvider: () -> String,
        logRequests: Boolean = false
    ): WhereIsMyCarApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider, languageProvider))
            .authenticator(TokenRefreshAuthenticator(tokenProvider))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .apply {
                if (logRequests) {
                    // BASIC logs method, URL and status only, never the token or the bodies
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    })
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WhereIsMyCarApi::class.java)
    }

    /** Client for the configured API, authenticated with the current Firebase user. */
    val default: WhereIsMyCarApi by lazy {
        create(BuildConfig.API_BASE_URL, firebaseTokenProvider, ::currentLanguage, BuildConfig.DEBUG)
    }

    private val firebaseTokenProvider = TokenProvider { forceRefresh ->
        val user = FirebaseAuth.getInstance().currentUser ?: return@TokenProvider null
        try {
            // OkHttp interceptors run on a background thread, so blocking here is fine
            Tasks.await(user.getIdToken(forceRefresh), 10, TimeUnit.SECONDS).token
        } catch (e: Exception) {
            Log.w(TAG, "Could not get the Firebase ID token", e)
            null
        }
    }

    /** The in app language if the user chose one, otherwise the device language. */
    private fun currentLanguage(): String {
        val appLocale = AppCompatDelegate.getApplicationLocales().get(0)
        return (appLocale ?: Locale.getDefault()).toLanguageTag()
    }
}
