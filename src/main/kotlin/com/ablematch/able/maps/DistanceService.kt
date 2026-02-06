package com.ablematch.able.maps

import org.springframework.stereotype.Service
import kotlin.math.roundToInt

data class LatLng(val lat: Double, val lng: Double)

data class DistanceEstimateResult(
    val origin: LatLng?,
    val destination: LatLng?,
    val distanceMeters: Int,
    val durationMs: Long,
    val durationMinutes: Int
)

@Service
class DistanceService(
    private val naver: NaverMapsClient
) {
    fun geocodeToLatLng(address: String): LatLng? {

        fun cleanAddress(addr: String): String {
            return addr
                .replace(Regex("\\(.*?\\)"), "")   // remove parentheses
                .replace(Regex("\\d+층"), "")      // remove floor
                .replace(Regex("빌라|센터|타워|아파트"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun toLatLng(a: GeocodeAddress?): LatLng? {
            val lng = a?.x?.toDoubleOrNull()
            val lat = a?.y?.toDoubleOrNull()
            return if (lng != null && lat != null) LatLng(lat, lng) else null
        }

        val cleaned = cleanAddress(address)
        println("GEOCODE CLEANED: $cleaned")

        val cleanRes = naver.geocode(cleaned).addresses.firstOrNull()
        toLatLng(cleanRes)?.let { return it }

        println("GEOCODE RAW FALLBACK: $address")
        val rawRes = naver.geocode(address).addresses.firstOrNull()
        return toLatLng(rawRes)
    }



    fun reverseToAddress(lat: Double, lng: Double): String {
        val res = naver.reverseGeocode(lat, lng)

        val first = res.results.firstOrNull()
            ?: return "UNKNOWN"

        val region = first.region
        val land = first.land

        val addr1 = region?.area1?.name ?: ""
        val addr2 = region?.area2?.name ?: ""
        val road = land?.roadName ?: ""
        val num = land?.number1 ?: ""

        return "$addr1 $addr2 $road $num".trim()
    }



    fun estimateByAddresses(
        originAddress: String,
        destinationAddress: String,
        option: String = "trafast"
    ): DistanceEstimateResult {
        val origin = geocodeToLatLng(originAddress)
        val dest = geocodeToLatLng(destinationAddress)

        if (origin == null || dest == null) {
            return DistanceEstimateResult(
                origin = origin,
                destination = dest,
                distanceMeters = 0,
                durationMs = 0,
                durationMinutes = 0
            )
        }

        val start = "${origin.lng},${origin.lat}"
        val goal = "${dest.lng},${dest.lat}"


        val driving = naver.driving(start = start, goal = goal, option = option)
        val routes = driving.route[option]
            ?: driving.route.values.firstOrNull()
            ?: throw IllegalStateException("No route returned")

        val summary = routes.firstOrNull()?.summary
            ?: throw IllegalStateException("No summary returned")

        val distance = summary.distance ?: 0
        val duration = summary.duration ?: 0L

        return DistanceEstimateResult(
            origin = origin,
            destination = dest,
            distanceMeters = distance,
            durationMs = duration,
            durationMinutes = (duration / 60_000.0).roundToInt()
        )
    }
}
