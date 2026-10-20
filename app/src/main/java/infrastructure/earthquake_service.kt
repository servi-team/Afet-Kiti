package infrastructure

import retrofit2.Response
import retrofit2.http.GET

interface earthquake_service {
    @GET("live.php")
    suspend fun getEarthquakes(): Response<EarthquakeResponse>
}
