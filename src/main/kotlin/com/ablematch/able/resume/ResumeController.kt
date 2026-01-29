package com.ablematch.able.resume

import com.ablematch.able.ai.OpenAiClient
import com.ablematch.able.auth.UserRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/resume")
class ResumeController(
    private val extractor: ResumeTextExtractor,
    private val openAiClient: OpenAiClient,
    private val resumeRepository: ResumeRepository,
    private val userRepository: UserRepository
) {


    @PostMapping("/upload")
    fun upload(
        @AuthenticationPrincipal user: UserDetails,
        @RequestParam("file") file: MultipartFile
    ): Resume {
        val dbUser = userRepository.findByEmail(user.username)
            ?: throw RuntimeException("User not found")

        val text = extractor.extract(file)

        val aiResult = openAiClient.extractResumeStructured(text)

        val resume = Resume(
            userId = dbUser.id!!,
            skills = aiResult.skills,
            accessibilityNeeds = aiResult.accessibility_needs,
            workType = aiResult.work_type
        )

        val existing = resumeRepository.findByUserId(dbUser.id!!)
        if (existing != null) {
            existing.skills = aiResult.skills as MutableList<String>
            existing.accessibilityNeeds = aiResult.accessibility_needs as MutableList<String>
            existing.workType = aiResult.work_type
            return resumeRepository.save(existing)
        }
        return resumeRepository.save(resume)
    }

}


