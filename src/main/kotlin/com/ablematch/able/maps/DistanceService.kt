package com.ablematch.able.maps

import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(DistanceService::class.java)
    fun geocodeToLatLng(address: String): LatLng? {

        fun cleanAddress(addr: String): String {
            return addr
                .replace(Regex("\\(.*?\\)"), "")
                .replace(Regex("\\d+층"), "")
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
        log.info("GEOCODE CLEANED: {}", cleaned)

        val cleanRes = naver.geocode(cleaned).addresses.firstOrNull()
        val cleanLatLng = toLatLng(cleanRes)
        if (cleanLatLng != null) {
            log.info("GEOCODE SUCCESS CLEANED -> {}", cleanLatLng)
            return cleanLatLng
        }

        log.warn("GEOCODE CLEAN FAILED, RAW FALLBACK: {}", address)

        val rawRes = naver.geocode(address).addresses.firstOrNull()
        val rawLatLng = toLatLng(rawRes)

        if (rawLatLng != null) {
            log.info("GEOCODE SUCCESS RAW -> {}", rawLatLng)
        } else {
            log.error("GEOCODE FAILED COMPLETELY: {}", address)
        }

        return rawLatLng
    }




    fun reverseToAddress(lat: Double, lng: Double): String {
        val res = naver.reverseGeocode(lat, lng)
        val first = res.results.firstOrNull() ?: return "UNKNOWN"

        val region = first.region
        val land = first.land

        val area1 = region?.area1?.name ?: ""
        val area2 = region?.area2?.name ?: ""

        val road = land?.roadName
        val num = land?.number1

        if (!road.isNullOrBlank() && !num.isNullOrBlank()) {
            return "$area1 $area2 $road $num".trim()
        }

        if (!road.isNullOrBlank()) {
            return "$area1 $area2 $road".trim()
        }

        if (area1.isNotBlank() || area2.isNotBlank()) {
            return "$area1 $area2".trim()
        }

        return "UNKNOWN"
    }



    fun estimateByAddresses(
        originAddress: String,
        destinationAddress: String,
        option: String = "trafast"
    ): DistanceEstimateResult {

        log.info("DISTANCE REQUEST origin='{}' dest='{}'", originAddress, destinationAddress)

        val origin = geocodeToLatLng(originAddress)
        val dest = geocodeToLatLng(destinationAddress)

        log.info("GEOCODE RESULT origin={} dest={}", origin, dest)

        if (origin == null || dest == null) {
            log.warn("DISTANCE SKIPPED: origin or destination null")
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

        log.info("NAVER DRIVING start={} goal={}", start, goal)

        val driving = naver.driving(start = start, goal = goal, option = option)

        val routes = driving.route[option]
            ?: driving.route.values.firstOrNull()
            ?: run {
                log.error("NO ROUTES RETURNED FROM NAVER")
                throw IllegalStateException("No route returned")
            }

        val summary = routes.firstOrNull()?.summary
            ?: run {
                log.error("NO SUMMARY RETURNED FROM NAVER")
                throw IllegalStateException("No summary returned")
            }

        val distance = summary.distance ?: 0
        val duration = summary.duration ?: 0L

        log.info("DISTANCE SUCCESS distance={}m duration={}ms", distance, duration)

        return DistanceEstimateResult(
            origin = origin,
            destination = dest,
            distanceMeters = distance,
            durationMs = duration,
            durationMinutes = (duration / 60_000.0).roundToInt()
        )
    }

}
