package com.ablematch.able.resume

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ResumeRepository : JpaRepository<Resume, UUID> {
    fun findByUserId(userId: UUID): Resume?

}