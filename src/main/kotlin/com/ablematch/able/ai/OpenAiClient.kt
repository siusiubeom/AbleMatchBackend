package com.ablematch.able.ai

import com.ablematch.able.auth.ExtractedProfile
import com.ablematch.able.dto.JobAIResult
import com.ablematch.able.dto.ResumeAIResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException


@Component
class OpenAiClient(
    @Value("\${openai.api-key}") private val apiKey: String,
    @Value("\${openai.model}") private val model: String,
) {

    private companion object {
        const val MAX_INPUT_CHARS = 200_000
    }

    private val PROFILE_SYSTEM_PROMPT = """
You are an AI system that extracts useful profile signals
from unstructured Korean resume text.

The resume may be messy, incomplete, or poorly formatted.

Your goal is NOT perfect accuracy.
Your goal is to extract the MOST REASONABLE values
that can be used for career matching.

==============================
GENERAL GUIDELINES
==============================

- Use common sense and context.
- Prefer clearly implied information over strict patterns.
- If a value is missing, output "UNKNOWN".
- Keep values concise and human-readable.
- Output must strictly match the provided JSON schema.

==============================
FIELDS
==============================

[NAME]

- Extract the person's name if it is clearly identifiable.
- Usually appears near:
  "이름", "기본 정보", email, or contact information.
- If multiple candidates exist, choose the most natural Korean name.
- If truly unclear, output "UNKNOWN".

------------------------------

[MAJOR]

- Extract the academic major or field of study.
- Look for education-related context:
  university, degree, coursework.
- Remove suffixes like "과", "학과".
- If not found, output "UNKNOWN".

------------------------------

[GPA]

- Extract GPA ONLY if explicitly written.
- Common format:
  "x.xx / 4.5"
- Do NOT guess.
- If not clearly present, output "UNKNOWN".

------------------------------

[PREFERRED ROLE]

- Extract the role the person most likely wants to work as.
- Prefer explicit statements:
  "희망 직무", "지원 직무".
- If not explicit, infer from:
  - project descriptions
  - self-introduction
  - repeated responsibilities
- Choose a GENERAL role category.
- Examples:
  "데이터 분석가", "서비스 기획", "백엔드 엔지니어"
  
------------------------------

[LOCATION]

- Extract the MOST PRECISE residential address available.
- Do NOT simplify or shorten unless necessary.
- Preserve district / street / building numbers if present.

Priority order:
1. Full street address (도로명 or 지번)
2. City + district
3. City/province
4. UNKNOWN

Examples:
- "서울특별시 강남구 테헤란로 123"
- "경기도 성남시 분당구"
- "부산 해운대구"
- "서울"

If multiple addresses appear, choose the most recent or most clearly labeled residence.
Do NOT output email or phone numbers.


==============================
OUTPUT FORMAT
==============================

Return ONLY valid JSON:

{
  "name": string,
  "major": string,
  "gpa": string,
  "preferredRole": string,
  "location": string
}

""".trimIndent()

    private val mapper: ObjectMapper =
        jacksonObjectMapper().registerKotlinModule()

    private val restClient = RestClient.builder()
        .baseUrl("https://api.openai.com/v1")
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()


    fun extractResumeStructured(text: String): ResumeAIResult {
        val inputText = prepareInput(text)

        val request = mapOf(
            "model" to model,
            "input" to listOf(
                systemMsg("Extract structured data strictly matching the given JSON schema."),
                userMsg(inputText)
            ),
            "text" to schema("resume_schema", AiSchemas.RESUME_SCHEMA)
        )

        val response = post(request)
        val json = extractJson(response)
        return mapper.convertValue(json, ResumeAIResult::class.java)
    }

    fun extractJobStructured(text: String): JobAIResult {
        val inputText = prepareInput(text)

        val request = mapOf(
            "model" to model,
            "input" to listOf(
                systemMsg(
                    """
You are extracting structured job data.

Rules:
- Jobs may be NON-SOFTWARE (cosmetics, sales, HR, marketing, design, manufacturing).
- NEVER default to software roles.
- NEVER return empty arrays for requiredSkills or accessibilityOptions.
- If skills are not listed, INFER them from responsibilities.
- If accessibility is not listed, INFER reasonable accommodations from work type.
- Prefer inference over UNKNOWN.
- If TITLE or COMPANY is explicitly provided above, USE IT EXACTLY.
- Only infer when missing.
- Do not return UNKNOWN if a reasonable value exists.
""".trimIndent()
                ),
                userMsg(inputText)
            ),
            "text" to schema("job_schema", AiSchemas.JOB_SCHEMA)
        )

        val response = post(request)
        val json = extractJson(response)
        return mapper.convertValue(json, JobAIResult::class.java)
    }
    private fun runExtraction(inputText: String): ExtractedProfile {
        val request = mapOf(
            "model" to model,
            "input" to listOf(
                systemMsg(PROFILE_SYSTEM_PROMPT),
                userMsg(
                    """
                ===== RESUME START =====
                $inputText
                ===== RESUME END =====
                """.trimIndent()
                )
            ),
            "text" to schema("profile_schema", AiSchemas.PROFILE_SCHEMA)
        )

        val response = post(request)
        val json = extractJson(response)
        return mapper.convertValue(json, ExtractedProfile::class.java)
    }
    private fun validateGpa(
        gpa: String,
        resumeText: String
    ): String {
        val normalized = resumeText.replace("\\s+".toRegex(), " ")

        val gpaRegex = Regex("""(\d\.\d{1,2})\s*/\s*4\.5""")

        val matches = gpaRegex.findAll(normalized).toList()

        if (matches.isNotEmpty()) {
            return matches.first().value
        }

        val strictGpaRegex = Regex("""\d\.\d{1,2}\s*/\s*4\.5""")
        if (strictGpaRegex.matches(gpa) && normalized.contains(gpa)) {
            return gpa
        }


        return "UNKNOWN"
    }

    private fun validateAndFix(
        extracted: ExtractedProfile,
        resumeText: String
    ): ExtractedProfile {

        val fixedGpa = validateGpa(extracted.gpa, resumeText)

        fun validateLocation(location: String): String {
            if (location.length > 120) return "UNKNOWN"
            if (location.contains("@")) return "UNKNOWN"
            return location
        }

        return extracted.copy(
            gpa = fixedGpa,
            preferredRole = validateRole(extracted.preferredRole, resumeText),
            location = validateLocation(extracted.location)
        )
    }
    private fun validateRole(
        role: String,
        resumeText: String
    ): String {

        if (resumeText.contains(role)) return role

        val backendKeywords = listOf("백엔드", "Backend", "Back-end")
        if (backendKeywords.any { role.contains(it) }) {
            return "소프트웨어 엔지니어"
        }

        return role
    }




    fun extractProfileFromResume(text: String): ExtractedProfile {
        println("===== RAW INPUT TO OPENAI =====")
        println(text)
        println("================================")
        val inputText = prepareInput(text)

        val extracted = runExtraction(inputText)
        return validateAndFix(extracted, inputText)
    }


    private fun post(body: Map<String, Any?>): Map<*, *> =
        try {
            restClient.post()
                .uri("/responses")
                .body(body)
                .retrieve()
                .body(Map::class.java)
                ?: error("OpenAI response null")
        } catch (e: HttpClientErrorException.TooManyRequests) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Resume is too large for processing. Please upload a shorter file."
            )
        }

    private fun prepareInput(text: String): String {
        val normalized =
            text
                .replace(Regex("\\r\\n|\\r"), "\n")
                .replace(Regex("\n{2,}"), "\n")
                .replace(Regex("\\s+"), " ")
                .replace(Regex("Page \\d+"), "")
                .trim()

        return if (normalized.length > MAX_INPUT_CHARS) {
            normalized.substring(0, MAX_INPUT_CHARS)
        } else {
            normalized
        }
    }


    private fun systemMsg(text: String) = mapOf(
        "role" to "system",
        "content" to listOf(mapOf("type" to "input_text", "text" to text))
    )

    private fun userMsg(text: String) = mapOf(
        "role" to "user",
        "content" to listOf(mapOf("type" to "input_text", "text" to text))
    )

    private fun schema(name: String, schema: Map<String, Any>) =
        mapOf(
            "format" to mapOf(
                "type" to "json_schema",
                "name" to name,
                "schema" to schema,
                "strict" to true
            )
        )

    private fun extractJson(response: Map<*, *>): Any {
        val output = response["output"] as? List<*>
            ?: error("No output field in OpenAI response")

        val message = output.firstOrNull() as? Map<*, *>
            ?: error("No message in output")

        val contents = message["content"] as? List<*>
            ?: error("No content in message")

        contents.firstOrNull { (it as Map<*, *>)["type"] == "output_json" }
            ?.let { return (it as Map<*, *>)["json"] ?: error("output_json missing json") }

        contents.firstOrNull { (it as Map<*, *>)["type"] == "output_text" }
            ?.let {
                val text = (it as Map<*, *>)["text"] as? String
                    ?: error("output_text missing text")
                return mapper.readValue(text, Map::class.java)
            }

        error("No output_json or output_text found in OpenAI response")
    }
}



object AiSchemas {

    val RESUME_SCHEMA = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("skills", "accessibility_needs", "work_type"),
        "properties" to mapOf(
            "skills" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "accessibility_needs" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "work_type" to mapOf(
                "type" to "string",
                "enum" to listOf("ONSITE", "REMOTE", "HYBRID", "UNKNOWN")
            )
        )
    )

    val JOB_SCHEMA = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("title", "company", "requiredSkills", "accessibilityOptions", "workType"),
        "properties" to mapOf(
            "title" to mapOf("type" to "string"),
            "company" to mapOf("type" to "string"),
            "requiredSkills" to mapOf(
                "type" to "array",
                "minItems" to 3,
                "items" to mapOf("type" to "string")
            ),
            "accessibilityOptions" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string")
            ),
            "workType" to mapOf(
                "type" to "string",
                "enum" to listOf("ONSITE", "REMOTE", "HYBRID", "UNKNOWN")
            )
        )
    )

    val PROFILE_SCHEMA = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("name", "major", "gpa", "preferredRole", "location"),
        "properties" to mapOf(
            "name" to mapOf("type" to "string"),
            "major" to mapOf("type" to "string"),
            "gpa" to mapOf("type" to "string"),
            "preferredRole" to mapOf("type" to "string"),
            "location" to mapOf("type" to "string")
        )
    )
}


@Service
class ResumeProfileExtractor(
    private val openAiClient: OpenAiClient
) {
    fun extract(resumeText: String): ExtractedProfile {
        return openAiClient.extractProfileFromResume(resumeText)
    }
}
