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
                .replace(Regex("\\(.*?\\)"), "")
                .replace(Regex("\\d+층"), "")
                .replace(Regex("빌라|센터|타워|아파트"), "")
                .trim()
        }

        val res = naver.geocode(address)
        val firstRaw = res.addresses.firstOrNull()

        if (firstRaw != null) {
            val lng = firstRaw.x?.toDoubleOrNull()
            val lat = firstRaw.y?.toDoubleOrNull()
            if (lng != null && lat != null) {
                return LatLng(lat, lng)
            }
        }

        val cleaned = cleanAddress(address)
        val retry = naver.geocode(cleaned)
        val firstClean = retry.addresses.firstOrNull() ?: return null

        val lng = firstClean.x?.toDoubleOrNull() ?: return null
        val lat = firstClean.y?.toDoubleOrNull() ?: return null

        return LatLng(lat, lng)
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
