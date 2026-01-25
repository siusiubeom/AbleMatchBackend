package com.ablematch.able.resume

import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import java.util.UUID

@Entity
class Resume(
    @Id @GeneratedValue
    val id: UUID? = null,

    val userId: UUID,

    @ElementCollection
    val skills: List<String>,

    @ElementCollection
    val accessibilityNeeds: List<String>,

    val workType: String
)
