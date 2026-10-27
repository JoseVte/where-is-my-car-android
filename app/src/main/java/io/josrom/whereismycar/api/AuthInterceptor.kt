package io.josrom.whereismycar.api

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/** Returns the Firebase ID token of the signed in user, or null if nobody is signed in. */
fun interface TokenProvider {
    fun token(forceRefresh: Boolean): String?
}

/** Adds the Firebase ID token and the app language (the API translates its error messages). */
class AuthInterceptor(
    private val tokenProvider: TokenProvider,
    private val languageProvider: () -> String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept-Language", languageProvider())
            .apply { tokenProvider.token(forceRefresh = false)?.let { header("Authorization", "Bearer $it") } }
            .build()

        return chain.proceed(request)
    }
}

/**
 * On a 401, asks Firebase for a fresh token and retries once. Firebase caches ID tokens for an hour,
 * so a cached token can be rejected right after it expires.
 */
class TokenRefreshAuthenticator(private val tokenProvider: TokenProvider) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) {
            return null
        }

        val token = tokenProvider.token(forceRefresh = true) ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }
}
