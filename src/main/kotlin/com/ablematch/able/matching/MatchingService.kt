package com.ablematch.able.matching

import com.ablematch.able.dto.MatchingCardDto
import com.ablematch.able.dto.MatchingExplainDto
import com.ablematch.able.dto.MatchingWeightService
import com.ablematch.able.dto.MatchingWithCoursesDto
import com.ablematch.able.job.Job
import com.ablematch.able.job.JobRepository
import com.ablematch.able.recommend.CourseRecommendationService
import com.ablematch.able.resume.Resume
import com.ablematch.able.resume.ResumeRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MatchingService(
    private val resumeRepo: ResumeRepository,
    private val jobRepo: JobRepository,
    private val weightService: MatchingWeightService
) {

    fun match(userId: UUID): MatchingResponse {
        val resume = resumeRepo.findByUserId(userId)
            ?: return MatchingResponse(MatchingStatus.NO_RESUME)

        val jobs = jobRepo.findAllWithDetails()
        if (jobs.isEmpty()) {
            return MatchingResponse(MatchingStatus.NO_JOBS)
        }

        val results = jobs.mapNotNull { job ->
            val impossible =
                resume.accessibilityNeeds.contains("휠체어") &&
                        !job.accessibilityOptions.contains("엘리베이터")

            if (impossible) return@mapNotNull null

            val score = calculate(resume, job)

            MatchingCardDto(
                jobId = job.id!!,
                title = job.title,
                company = job.company,
                score = score,
                highlights = buildHighlights(resume, job),
                workType = job.workType,
                sourceUrl = job.sourceUrl
            )
        }.sortedByDescending { it.score }

        if (results.isEmpty()) {
            return MatchingResponse(MatchingStatus.NO_MATCH)
        }

        return MatchingResponse(MatchingStatus.READY, results)
    }



    private fun calculate(resume: Resume, job: Job): Int {
        val w = weightService.get()

        val skillScore =
            resume.skills.intersect(job.requiredSkills).size * w.skillWeight

        val accessibilityScore =
            resume.accessibilityNeeds.intersect(job.accessibilityOptions).size * w.accessibilityWeight

        val workTypeScore =
            if (resume.workType == job.workType) w.workTypeWeight else 0

        val rawScore = skillScore + accessibilityScore + workTypeScore

        val scaledScore = (rawScore * 1.3) + 45

        return scaledScore
            .coerceIn(55.0, 88.0).toInt()
    }



    private fun buildHighlights(resume: Resume, job: Job): List<String> {
        val highlights = mutableListOf<String>()

        if (resume.skills.intersect(job.requiredSkills).isNotEmpty())
            highlights.add("전공 일치")

        if (job.workType == "REMOTE")
            highlights.add("재택 가능")

        if (resume.accessibilityNeeds.intersect(job.accessibilityOptions).isNotEmpty())
            highlights.add("배리어프리")

        return highlights
    }

    private fun isImpossible(resume: Resume, job: Job): Boolean {
        if (
            resume.accessibilityNeeds.contains("휠체어") &&
            !job.accessibilityOptions.contains("엘리베이터")
        ) return true

        if (
            resume.workType == "REMOTE" &&
            job.workType == "ONSITE"
        ) return true

        return false
    }

    fun explain(resume: Resume, job: Job): MatchingExplainDto {
        val skillScore = resume.skills.intersect(job.requiredSkills).size * 10
        val accessScore =
            resume.accessibilityNeeds.intersect(job.accessibilityOptions).size * 15
        val workScore = if (resume.workType == job.workType) 20 else 0

        val impossible =
            resume.accessibilityNeeds.contains("휠체어") &&
                    !job.accessibilityOptions.contains("엘리베이터")

        return MatchingExplainDto(
            jobTitle = job.title,
            score = if (impossible) 0 else (skillScore + accessScore + workScore),
            breakdown = mapOf(
                "skill" to skillScore,
                "accessibility" to accessScore,
                "workType" to workScore
            ),
            missingSkills = job.requiredSkills.filterNot { resume.skills.contains(it) },
            impossibleReason = if (impossible) "엘리베이터 미비" else null
        )
    }


}

enum class MatchingStatus {
    NO_RESUME,
    NO_JOBS,
    NO_MATCH,
    READY
}

data class MatchingResponse(
    val status: MatchingStatus,
    val data: List<MatchingCardDto> = emptyList()
)

