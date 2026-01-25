package com.ablematch.able.job

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JobRepository : JpaRepository<Job, UUID>