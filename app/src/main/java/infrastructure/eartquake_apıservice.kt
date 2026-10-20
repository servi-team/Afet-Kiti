package infrastructure

import retrofit2.http.GET

interface eartquake_apıservice
{@GET("zeqmap/xml/aktuel.xml ") suspend fun getEarthquakes(): earthquakeresponse

    annotation class earthquakeresponse
}
