package com.ablematch.able.matching

import com.ablematch.able.auth.UserRepository
import com.ablematch.able.dto.MatchingCardDto
import com.ablematch.able.dto.MatchingExplainDto
import com.ablematch.able.dto.MatchingWithCoursesDto
import com.ablematch.able.job.JobRepository
import com.ablematch.able.resume.ResumeRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/matching")
class MatchingController(
    private val matchingService: MatchingService,
    private val userRepository: UserRepository,
    private val resumeRepository: ResumeRepository,
    private val jobRepository: JobRepository
) {

    @GetMapping
    fun getMatching(
        @AuthenticationPrincipal user: UserDetails
    ): MatchingResponse {

        val dbUser = userRepository.findByEmail(user.username)
            ?: throw RuntimeException("User not found")

        return matchingService.match(dbUser.id!!)
    }



    @GetMapping("/{jobId}/explain")
    fun explain(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable jobId: UUID
    ): MatchingExplainDto {
        val u = userRepository.findByEmail(user.username)!!
        val resume = resumeRepository.findByUserId(u.id!!)!!
        val job = jobRepository.findByIdWithDetails(jobId)
            ?: throw RuntimeException("Job not found")
        return matchingService.explain(resume, job)
    }

}

