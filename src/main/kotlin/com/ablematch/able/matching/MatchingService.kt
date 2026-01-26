package com.ablematch.able.matching

import com.ablematch.able.dto.MatchingCardDto
import com.ablematch.able.dto.MatchingExplainDto
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
    private val jobRepo: JobRepository
) {

    fun match(userId: UUID): List<MatchingCardDto> {
        val resume = resumeRepo.findByUserId(userId)
            ?: throw RuntimeException("Resume not found")

        return jobRepo.findAll()
            .mapNotNull { job ->
                val impossible =
                    resume.accessibilityNeeds.contains("휠체어") &&
                            !job.accessibilityOptions.contains("엘리베이터")

                if (impossible) return@mapNotNull null

                val score = calculate(resume, job)

                MatchingCardDto(
                    jobId = job.id!!,
                    title = job.title,
                    company = "비공개", // MVP
                    score = score,
                    highlights = buildHighlights(resume, job),
                    workType = job.workType
                )
            }
            .sortedByDescending { it.score }
    }

    private fun calculate(resume: Resume, job: Job): Int {
        val skillScore = resume.skills.intersect(job.requiredSkills).size * 10
        val accessibilityScore =
            resume.accessibilityNeeds.intersect(job.accessibilityOptions).size * 15
        val workTypeScore = if (resume.workType == job.workType) 20 else 0

        return (skillScore + accessibilityScore + workTypeScore)
            .coerceAtMost(100)
    }

    private fun buildHighlights(resume: Resume, job: Job): List<String> {
        val highlights = mutableListOf<String>()

        if (resume.skills.intersect(job.requiredSkills).isNotEmpty())
            highlights.add("스킬 일치")

        if (resume.workType == job.workType)
            highlights.add("근무 형태 일치")

        if (resume.accessibilityNeeds.intersect(job.accessibilityOptions).isNotEmpty())
            highlights.add("배리어프리 환경")

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

