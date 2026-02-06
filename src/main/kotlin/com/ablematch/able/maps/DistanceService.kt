package com.ablematch.able.maps

import org.springframework.stereotype.Service
import kotlin.math.roundToInt

data class LatLng(val lat: Double, val lng: Double)

data class DistanceEstimateResult(
    val origin: LatLng,
    val destination: LatLng,
    val distanceMeters: Int,
    val durationMs: Long,
    val durationMinutes: Int
)

@Service
class DistanceService(
    private val naver: NaverMapsClient
) {
    fun geocodeToLatLng(address: String): LatLng {
        val res = naver.geocode(address)
        val first = res.addresses.firstOrNull()
            ?: throw IllegalArgumentException("No geocode result for: $address")

        val lng = first.x?.toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid geocode x for: $address")
        val lat = first.y?.toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid geocode y for: $address")

        return LatLng(lat = lat, lng = lng)
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
