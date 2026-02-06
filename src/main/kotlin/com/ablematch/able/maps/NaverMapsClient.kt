package com.ablematch.able.maps

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder

@Component
class NaverMapsClient(
    @Value("\${naver.maps.base-url}") private val baseUrl: String,
    @Value("\${naver.maps.key-id}") private val keyId: String,
    @Value("\${naver.maps.key}") private val key: String,
) {

    private val client = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()


    fun geocode(query: String, coordinate: String? = null): GeocodeResponse {
        val uri = UriComponentsBuilder
            .fromPath("/map-geocode/v2/geocode")
            .queryParam("query", query)
            .apply { if (!coordinate.isNullOrBlank()) queryParam("coordinate", coordinate) }
            .build()
            .encode()
            .toUriString()
        return client.get()
            .uri(uri)
            .header("X-NCP-APIGW-API-KEY-ID", keyId)
            .header("X-NCP-APIGW-API-KEY", key)
            .retrieve()
            .body(GeocodeResponse::class.java)
            ?: error("Geocode response null")
    }

    fun reverseGeocode(lat: Double, lng: Double): ReverseGeocodeResponse {
        val uri = UriComponentsBuilder
            .fromPath("/map-reversegeocode/v2/gc")
            .queryParam("coords", "$lng,$lat")
            .queryParam("orders", "roadaddr")
            .queryParam("output", "json")
            .build()
            .encode()
            .toUriString()

        return client.get()
            .uri(uri)
            .header("X-NCP-APIGW-API-KEY-ID", keyId)
            .header("X-NCP-APIGW-API-KEY", key)
            .retrieve()
            .body(ReverseGeocodeResponse::class.java)
            ?: error("Reverse geocode null")
    }


    fun driving(start: String, goal: String, option: String = "trafast"): DrivingResponse {
        val uri = UriComponentsBuilder
            .fromPath("/map-direction/v1/driving")
            .queryParam("start", start)
            .queryParam("goal", goal)
            .queryParam("option", option)
            .build()
            .encode()
            .toUriString()
        println("NAVER KEY ID = $keyId")
        println("NAVER KEY = $key")

        return client.get()
            .uri(uri)
            .header("X-NCP-APIGW-API-KEY-ID", keyId)
            .header("X-NCP-APIGW-API-KEY", key)
            .retrieve()
            .body(DrivingResponse::class.java)
            ?: error("Driving response null")

    }
}

data class ReverseGeocodeResponse(
    val results: List<ReverseResult>
)

data class ReverseResult(
    val region: Region?,
    val land: Land?
)

data class Region(
    val area1: Area?,
    val area2: Area?
)

data class Area(
    val name: String?
)

data class Land(
    val roadName: String?,
    val number1: String?
)
