package com.ablematch.able.recommend

import com.ablematch.able.ai.OpenAiClient
import com.ablematch.able.auth.UserRepository
import com.ablematch.able.job.Job
import com.ablematch.able.resume.Resume
import com.ablematch.able.resume.ResumeRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/resume")
class ResumeController(
    private val openAiClient: OpenAiClient,
    private val resumeRepository: ResumeRepository,
    private val userRepository: UserRepository
) {

    @PostMapping
    fun upload(
        @AuthenticationPrincipal user: UserDetails,
        @RequestBody request: ResumeUploadRequest
    ): Resume {
        val dbUser = userRepository.findByEmail(user.username)
            ?: throw RuntimeException("User not found")

        val aiResult = openAiClient.extractResumeStructured(request.text)

        val resume = Resume(
            userId = dbUser.id!!,
            skills = aiResult.skills,
            accessibilityNeeds = aiResult.accessibility_needs,
            workType = aiResult.work_type
        )

        return resumeRepository.save(resume)
    }
}

data class ResumeUploadRequest(
    val text: String
)

