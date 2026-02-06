package com.ablematch.able.maps

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/maps")
class MapsController(
    private val distanceService: DistanceService
) {
    @GetMapping("/estimate-test")
    fun estimateTest(): DistanceEstimateResult {
        return distanceService.estimateByAddresses(
            originAddress = "서울특별시 강남구 테헤란로 152",
            destinationAddress = "서울특별시 마포구 마포대로 122"
        )
    }
    @GetMapping("/geocode")
    fun geocode(
        @RequestParam query: String
    ): LatLng? {
        return distanceService.geocodeToLatLng(query)
    }

    @GetMapping("/reverse")
    fun reverse(
        @RequestParam lat: Double,
        @RequestParam lng: Double
    ): String {
        return distanceService.reverseToAddress(lat, lng)
    }


    @GetMapping("/estimate")
    fun estimate(
        @RequestParam originAddress: String,
        @RequestParam destinationAddress: String,
        @RequestParam(required = false, defaultValue = "trafast") option: String
    ): DistanceEstimateResult {
        return distanceService.estimateByAddresses(
            originAddress = originAddress,
            destinationAddress = destinationAddress,
            option = option
        )
    }
}
