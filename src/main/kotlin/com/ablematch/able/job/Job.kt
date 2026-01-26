package com.ablematch.able.job

import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import java.util.UUID

@Entity
class Job(
    @Id @GeneratedValue
    val id: UUID? = null,

    val title: String,

    val company: String,

    @ElementCollection
    val requiredSkills: List<String>,

    @ElementCollection
    val accessibilityOptions: List<String>,

    val workType: String
)
