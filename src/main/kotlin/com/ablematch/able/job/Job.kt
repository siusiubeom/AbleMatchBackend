package com.ablematch.able.job

import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import org.hibernate.annotations.BatchSize
import java.time.Instant
import java.util.UUID


@Entity
open class Job protected constructor() {

    @Id @GeneratedValue
    open var id: UUID? = null

    open lateinit var title: String
    open lateinit var company: String

    @Column(unique = true, nullable = false)
    open lateinit var sourceHash: String

    @Column(nullable = false)
    open lateinit var sourceUrl: String

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    open var requiredSkills: Set<String> = mutableSetOf()

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    open var accessibilityOptions: List<String> = mutableListOf()

    open lateinit var workType: String

    @Column(nullable = false)
    open lateinit var lastFetchedAt: Instant

    @Column(nullable = false)
    open var likeCount: Long = 0

    @Column(nullable = false)
    open var viewCount: Long = 0


    companion object {
        fun create(sourceHash: String, sourceUrl: String): Job =
            Job().apply {
                this.sourceHash = sourceHash
                this.sourceUrl = sourceUrl
                this.lastFetchedAt = Instant.EPOCH
            }
    }
}
data class WantedApiResponse(val data: List<WantedJobSummary>)
data class WantedJobSummary(val id: Int)

@Entity
class JobSource(
    val company: String,
    val platform: JobPlatform,
    val listUrl: String,
    val active: Boolean = true
) {
    @Id @GeneratedValue
    var id: UUID? = null
}

enum class JobPlatform {
    WANTED,
    SARAMIN,
    JOBKOREA
}
