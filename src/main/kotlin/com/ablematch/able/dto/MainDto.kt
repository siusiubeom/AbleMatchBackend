package com.ablematch.able.dto

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
