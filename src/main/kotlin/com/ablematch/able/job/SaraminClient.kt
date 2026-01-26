package com.ablematch.able.job

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class SaraminClient(
    @Value("\${saramin.api-key}") private val apiKey: String
) {
    private val client = RestClient.create()

    fun fetchJobs(): List<String> {
        val res = client.get()
            .uri {
                it.scheme("https")
                    .host("oapi.saramin.co.kr")
                    .path("/job-search")
                    .queryParam("access-key", apiKey)
                    .queryParam("count", 20)
                    .build()
            }
            .retrieve()
            .body(Map::class.java)

        val jobs = (res?.get("jobs") ?: "") as Map<*, *>
        val jobList = jobs["job"] as List<Map<*, *>>

        return jobList.mapNotNull { job ->
            val position = job["position"] as? Map<*, *> ?: return@mapNotNull null
            position["job-description"]?.toString()
        }
    }
}
