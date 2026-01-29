package com.ablematch.able.resume

import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import java.util.UUID

@Entity
open class Resume protected constructor() {

    @Id
    @GeneratedValue
    open var id: UUID? = null

    @Column(name = "user_id", nullable = false)
    open lateinit var userId: UUID

    @Column(nullable = false)
    open lateinit var workType: String

    @ElementCollection(fetch = FetchType.EAGER)
    open var accessibilityNeeds: MutableList<String> = mutableListOf()

    @ElementCollection(fetch = FetchType.EAGER)
    open var skills: MutableList<String> = mutableListOf()

    constructor(
        userId: UUID,
        skills: List<String>,
        accessibilityNeeds: List<String>,
        workType: String
    ) : this() {
        this.userId = userId
        this.skills = skills.toMutableList()
        this.accessibilityNeeds = accessibilityNeeds.toMutableList()
        this.workType = workType
    }
}
