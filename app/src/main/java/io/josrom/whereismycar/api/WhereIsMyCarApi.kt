package io.josrom.whereismycar.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface WhereIsMyCarApi {
    /** Creates the user in the API the first time, so it must be called before anything else. */
    @GET("api/me")
    suspend fun me(): ApiUser

    @GET("api/categories")
    suspend fun categories(): List<ApiCategory>

    @POST("api/categories")
    suspend fun createCategory(@Body body: CategoryBody): ApiCategory

    @PUT("api/categories/{id}")
    suspend fun updateCategory(@Path("id") id: Int, @Body body: CategoryBody): ApiCategory

    @DELETE("api/categories/{id}")
    suspend fun deleteCategory(@Path("id") id: Int): Response<Unit>

    @GET("api/saved-locations")
    suspend fun savedLocations(): List<ApiSavedLocation>

    @POST("api/saved-locations")
    suspend fun createSavedLocation(@Body body: SavedLocationBody): ApiSavedLocation

    @PUT("api/saved-locations/{id}")
    suspend fun updateSavedLocation(@Path("id") id: Int, @Body body: SavedLocationBody): ApiSavedLocation

    @DELETE("api/saved-locations/{id}")
    suspend fun deleteSavedLocation(@Path("id") id: Int): Response<Unit>
}
