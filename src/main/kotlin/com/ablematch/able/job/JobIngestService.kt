package com.ablematch.able.job

import com.ablematch.able.ai.OpenAiClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class JobIngestService(
    private val saraminClient: SaraminClient,
    private val openAiClient: OpenAiClient,
    private val jobRepo: JobRepository
) {

    @Scheduled(cron = "0 0 0 * * *")
    fun ingest() {
        saraminClient.fetchJobs().forEach { raw ->
            val ai = openAiClient.extractJobStructured(raw)
            jobRepo.save(
                Job(
                    title = ai.title,
                    company = "Saramin",
                    requiredSkills = ai.requiredSkills,
                    accessibilityOptions = ai.accessibilityOptions,
                    workType = ai.workType
                )
            )
        }
    }
}
