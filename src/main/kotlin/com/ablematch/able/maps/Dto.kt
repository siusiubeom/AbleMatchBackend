package com.ablematch.able.maps

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeocodeResponse(
    val status: String? = null,
    val errorMessage: String? = null,
    val addresses: List<GeocodeAddress> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeocodeAddress(
    val roadAddress: String? = null,
    val jibunAddress: String? = null,
    val englishAddress: String? = null,
    val x: String? = null,
    val y: String? = null,
    val distance: Double? = null
)


@JsonIgnoreProperties(ignoreUnknown = true)
data class DrivingResponse(
    val code: Int = -1,
    val message: String? = null,
    val route: Map<String, List<DrivingRoute>> = emptyMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DrivingRoute(
    val summary: DrivingSummary? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DrivingSummary(
    val distance: Int? = null,
    val duration: Long? = null
)

data class JobBoardDto(
    val id: UUID,
    val title: String,
    val company: String,
    val workType: String,
    val sourceUrl: String,
    val viewCount: Long,
    val likeCount: Long
)

