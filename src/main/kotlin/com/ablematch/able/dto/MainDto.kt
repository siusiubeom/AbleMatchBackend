package com.ablematch.able.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import java.util.UUID

data class ResumeAIResult(
    val skills: List<String>,
    val accessibility_needs: List<String>,
    val work_type: String
)

data class OpenAiResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

data class Message(
    val content: String
)

data class MatchingResultDto(
    val jobTitle: String,
    val score: Int,
    val missingSkills: List<String>
)


data class MatchingWithCoursesDto(
    val jobTitle: String,
    val score: Int,
    val missingSkills: List<String>,
    val recommendedCourses: List<RecommendedCourseDto>
)


data class RecommendedCourseDto(
    val skill: String,
    val title: String,
    val url: String
)

data class MatchingCardDto(
    val jobId: UUID,
    val title: String,
    val company: String,
    val score: Int,
    val highlights: List<String>,
    val workType: String,
    val sourceUrl: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JobAIResult(
    val title: String = "",
    val requiredSkills: List<String> = emptyList(),
    val accessibilityOptions: List<String> = emptyList(),
    val workType: String = "UNKNOWN",
    val company: String = ""
)



data class JobRawUploadRequest(
    val company: String,
    val text: String
)

data class MatchingExplainDto(
    val jobTitle: String,
    val score: Int,
    val breakdown: Map<String, Int>,
    val missingSkills: List<String>,
    val impossibleReason: String?
)

@Entity
class MatchingWeight(
    @Id
    val id: Long = 1,

    var skillWeight: Int = 10,
    var accessibilityWeight: Int = 15,
    var workTypeWeight: Int = 20
)

interface MatchingWeightRepository : JpaRepository<MatchingWeight, Long>

@Service
class MatchingWeightService(
    private val repo: MatchingWeightRepository
) {
    fun get(): MatchingWeight =
        repo.findById(1).orElseGet { repo.save(MatchingWeight()) }
}


