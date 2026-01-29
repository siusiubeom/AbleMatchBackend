package com.ablematch.able.job

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface JobRepository : JpaRepository<Job, UUID> {
    fun findBySourceHash(sourceHash: String): Job?

    @Query(""" select distinct j from Job j left join fetch j.requiredSkills left join fetch j.accessibilityOptions """)
    fun findAllWithDetails(): List<Job>
    @Query(""" select j from Job j left join fetch j.requiredSkills left join fetch j.accessibilityOptions where j.id = :id """)
    fun findByIdWithDetails(id: UUID): Job?
}

interface JobSourceRepository : JpaRepository<JobSource, UUID> {
    fun findAllByActiveTrue(): List<JobSource>
}