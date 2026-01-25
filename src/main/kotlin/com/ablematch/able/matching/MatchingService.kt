package com.ablematch.able.matching

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
    private val courseService: CourseRecommendationService
) {

    fun matchWithCourses(userId: UUID): List<MatchingWithCoursesDto> {
        val resume = resumeRepo.findByUserId(userId)
            ?: throw RuntimeException("Resume not found")

        return jobRepo.findAll().map { job ->
            val missing = missingSkills(resume, job)

            MatchingWithCoursesDto(
                jobTitle = job.title,
                score = calculate(resume, job),
                missingSkills = missing,
                recommendedCourses = courseService.recommendBySkills(missing)
            )
        }.sortedByDescending { it.score }
    }

    private fun missingSkills(resume: Resume, job: Job): List<String> =
        job.requiredSkills.filterNot { resume.skills.contains(it) }

    private fun calculate(resume: Resume, job: Job): Int {
        val skillScore = resume.skills.intersect(job.requiredSkills).size * 10
        val accessibilityScore =
            resume.accessibilityNeeds.intersect(job.accessibilityOptions).size * 15
        val workTypeScore = if (resume.workType == job.workType) 20 else 0

        return (skillScore + accessibilityScore + workTypeScore).coerceAtMost(100)
    }
}
