package infrastructure

data class EarthquakeResponse(
    val status: Boolean?,
    val result: List<EarthquakeItem>?
)

data class EarthquakeItem(
    val title: String?,
    val date: String?,
    val mag: Double?
)
