package com.ablematch.able.dto

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
    val workType: String
)

data class JobAIResult(
    val title: String,
    val requiredSkills: List<String>,
    val accessibilityOptions: List<String>,
    val workType: String
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