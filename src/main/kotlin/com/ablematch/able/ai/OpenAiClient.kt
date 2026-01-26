package com.ablematch.able.ai

import com.ablematch.able.dto.JobAIResult
import com.ablematch.able.dto.OpenAiResponse
import com.ablematch.able.dto.ResumeAIResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class OpenAiClient(
    @Value("\${openai.api-key}") private val apiKey: String,
    @Value("\${openai.model}") private val model: String,
) {
    private val mapper = ObjectMapper()

    private val restClient = RestClient.builder()
        .baseUrl("https://api.openai.com/v1")
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    fun extractResumeStructured(text: String): ResumeAIResult {
        val request = mapOf(
            "model" to model,
            "input" to text,
            "response_format" to mapOf(
                "type" to "json_schema",
                "json_schema" to AiSchemas.RESUME_SCHEMA
            )
        )

        val response = restClient.post()
            .uri("/responses")
            .body(request)
            .retrieve()
            .body(Map::class.java)
            ?: throw RuntimeException("OpenAI response null")

        val output = response["output"] as List<*>
        val first = output.first() as Map<*, *>
        val content = first["content"] as List<*>
        val json = (content.first() as Map<*, *>)["json"]

        return mapper.convertValue(json, ResumeAIResult::class.java)
    }

    fun extractJobStructured(text: String): JobAIResult {
        val request = mapOf(
            "model" to model,
            "input" to text,
            "response_format" to mapOf(
                "type" to "json_schema",
                "json_schema" to AiSchemas.JOB_SCHEMA
            )
        )

        val response = restClient.post()
            .uri("/responses")
            .body(request)
            .retrieve()
            .body(Map::class.java)
            ?: throw RuntimeException("OpenAI response null")

        val output = response["output"] as List<*>
        val first = output.first() as Map<*, *>
        val content = first["content"] as List<*>
        val json = (content.first() as Map<*, *>)["json"]

        return mapper.convertValue(json, JobAIResult::class.java)
    }



}

object AiSchemas {

    val RESUME_SCHEMA = mapOf(
        "name" to "resume_schema",
        "schema" to mapOf(
            "type" to "object",
            "required" to listOf("skills", "accessibility_needs", "work_type"),
            "properties" to mapOf(
                "skills" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                "accessibility_needs" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                "work_type" to mapOf(
                    "type" to "string",
                    "enum" to listOf("ONSITE", "REMOTE", "HYBRID")
                )
            )
        ),
        "strict" to true
    )

    val JOB_SCHEMA = mapOf(
        "name" to "job_schema",
        "schema" to mapOf(
            "type" to "object",
            "required" to listOf("title", "requiredSkills", "accessibilityOptions", "workType"),
            "properties" to mapOf(
                "title" to mapOf("type" to "string"),
                "requiredSkills" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                "accessibilityOptions" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                "workType" to mapOf(
                    "type" to "string",
                    "enum" to listOf("ONSITE", "REMOTE", "HYBRID")
                )
            )
        ),
        "strict" to true
    )
}
