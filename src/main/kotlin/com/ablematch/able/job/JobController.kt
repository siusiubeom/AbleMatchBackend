package com.ablematch.able.job

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/jobs")
class JobController(
    private val jobRepository: JobRepository
) {
    @PostMapping
    fun create(@RequestBody req: JobCreateRequest): Job {
        val job = Job(
            title = req.title,
            requiredSkills = req.requiredSkills,
            accessibilityOptions = req.accessibilityOptions,
            workType = req.workType
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
