package com.ablematch.able.publicprofile

import com.ablematch.able.auth.User
import com.ablematch.able.auth.UserRepository
import com.ablematch.able.auth.UserProfileRepository
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID


@Entity
@Table(name = "user_public_profile")
class UserPublicProfile(

    @Id
    var id: UUID? = null,

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    @JsonIgnore
    var user: User,

    @Column
    var headline: String? = null,

    @Column(length = 5000)
    var bio: String? = null,

    @Column
    var portfolioUrl: String? = null,

    @Column
    var githubUrl: String? = null,

    @Column
    var linkedinUrl: String? = null,

    @Column
    var skills: String? = null,

    @Column
    var isPublic: Boolean = true
)


interface UserPublicProfileRepository : JpaRepository<UserPublicProfile, UUID> {
    fun findByUser(user: User): UserPublicProfile?
}


data class PublicProfileUpdateRequest(
    val headline: String?,
    val bio: String?,
    val portfolioUrl: String?,
    val githubUrl: String?,
    val linkedinUrl: String?,
    val skills: String?
)

/* =========================
   CONTROLLER
   ========================= */

@RestController
@RequestMapping("/api/me/public-profile")
class UserPublicProfileController(
    private val userRepository: UserRepository,
    private val publicRepo: UserPublicProfileRepository,
    private val profileRepo: UserProfileRepository
) {

    @GetMapping
    @Transactional
    fun getPublicProfile(auth: Authentication): UserPublicProfile {

        val user = userRepository.findByEmail(auth.name)
            ?: throw RuntimeException("User not found")

        var publicProfile = publicRepo.findByUser(user)

        if (publicProfile == null) {
            val base = profileRepo.findByUser(user)
                ?: throw RuntimeException("No base profile")

            publicProfile = UserPublicProfile(
                user = user,
                headline = base.preferredRole,
                bio = "${base.major} student"
            )

            publicRepo.save(publicProfile)
        }

        return publicProfile
    }

    @PutMapping
    fun updatePublicProfile(
        auth: Authentication,
        @RequestBody req: PublicProfileUpdateRequest
    ): UserPublicProfile {

        val user = userRepository.findByEmail(auth.name)
            ?: throw RuntimeException("User not found")

        val profile = publicRepo.findByUser(user)
            ?: throw RuntimeException("Public profile not found")

        req.headline?.let { profile.headline = it }
        req.bio?.let { profile.bio = it }
        req.portfolioUrl?.let { profile.portfolioUrl = it }
        req.githubUrl?.let { profile.githubUrl = it }
        req.linkedinUrl?.let { profile.linkedinUrl = it }
        req.skills?.let { profile.skills = it }

        return publicRepo.save(profile)
    }
}
