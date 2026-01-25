package com.ablematch.able.ai

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

    fun extractResume(text: String): ResumeAIResult {
        val request = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf(
                    "role" to "system",
                    "content" to """
                        Return ONLY valid minified JSON.
                        Do not include markdown.
                        Do not include explanation.
                    """.trimIndent()
                ),
                mapOf(
                    "role" to "user",
                    "content" to """
                        Extract skills, accessibility_needs, and work_type.
                        Format:
                        {
                          "skills": string[],
                          "accessibility_needs": string[],
                          "work_type": "ONSITE" | "REMOTE" | "HYBRID"
                        }

                        Resume:
                        $text
                    """.trimIndent()
                )
            )
        )

        val response = restClient.post()
            .uri("/chat/completions")
            .body(request)
            .retrieve()
            .body(OpenAiResponse::class.java)
            ?: throw RuntimeException("OpenAI response is null")

        val raw = response.choices.first().message.content.trim()

        val json =
            "{" + raw.substringAfter("{").substringBeforeLast("}") + "}"

        return mapper.readValue(json, ResumeAIResult::class.java)
    }
}
