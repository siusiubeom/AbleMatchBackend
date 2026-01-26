package com.ablematch.able.job

import com.ablematch.able.ai.OpenAiClient
import com.ablematch.able.dto.JobRawUploadRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/jobs")
class JobController(
    private val jobRepository: JobRepository,
    private val openAiClient: OpenAiClient
) {

    @PostMapping("/raw")
    fun uploadRaw(@RequestBody request: JobRawUploadRequest): Job {
        val aiResult = openAiClient.extractJobStructured(request.text)

        val job = Job(
            title = aiResult.title,
            company = request.company,
            requiredSkills = aiResult.requiredSkills,
            accessibilityOptions = aiResult.accessibilityOptions,
            workType = aiResult.workType
        )

        return jobRepository.save(job)
    }

    @GetMapping
    fun list(): List<Job> = jobRepository.findAll()
}


data class JobCreateRequest(
    val title: String,
    val requiredSkills: List<String>,
    val accessibilityOptions: List<String>,
    val workType: String
)
