package com.ablematch.able.auth

import com.ablematch.able.ai.OpenAiClient
import com.ablematch.able.ai.ResumeProfileExtractor
import com.ablematch.able.resume.ResumeTextExtractor
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.File
import java.util.UUID

@Entity
@Table(name = "users")
open class User protected constructor() {

    @Id
    @GeneratedValue
    open var id: UUID? = null

    @Column(unique = true, nullable = false)
    open lateinit var email: String

    @Column(nullable = false)
    open lateinit var password: String

    @Column(nullable = false)
    open var role: String = "USER"

    @OneToOne(
        mappedBy = "user",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE]
    )
    open var profile: UserProfile? = null


    constructor(
        email: String,
        password: String?,
        role: String = "USER"
    ) : this() {
        this.email = email
        if (password != null) this.password = password
        this.role = role
    }
}




@Entity
@Table(name = "user_profile")
class UserProfile(

    @Id
    var id: UUID? = null,

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    @JsonIgnore
    var user: User,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var major: String,

    @Column(nullable = false)
    var gpa: String,

    @Column(name = "preferred_role", nullable = false)
    var preferredRole: String,

    @Column
    var location: String? = null,

    @Column
    var profileImageUrl: String? = null

)




interface UserProfileRepository : JpaRepository<UserProfile, UUID> {
    fun findByUser(user: User): UserProfile?
}



data class ExtractedProfile(
    val name: String,
    val major: String,
    val gpa: String,
    val preferredRole: String,
    val location: String
)

@RestController
@RequestMapping("/api/me")
class ProfileFromResumeController(
    private val userRepository: UserRepository,
    private val profileRepository: UserProfileRepository,
    private val profileExtractor: ResumeProfileExtractor,
    private val resumeTextExtractor: ResumeTextExtractor
) {

    @GetMapping("/profile")
    fun myProfile(
        authentication: org.springframework.security.core.Authentication
    ): UserProfile {
        val email = authentication.name

        val user = userRepository.findByEmail(email)
            ?: throw RuntimeException("User not found")

        return profileRepository.findByUser(user)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Profile not created yet"
            )
    }

    @PutMapping("/profile")
    @Transactional
    fun updateProfile(
        authentication: Authentication,
        @RequestBody req: ProfileUpdateRequest
    ): UserProfile {

        val user = userRepository.findByEmail(authentication.name)!!
        val profile = profileRepository.findByUser(user)
            ?: throw RuntimeException("Profile not found")

        req.name?.let { profile.name = it }
        req.preferredRole?.let { profile.preferredRole = it }
        req.location?.let { profile.location = it }
        req.gpa?.let { profile.gpa = it }

        return profileRepository.save(profile)
    }

    @PostMapping("/profile/image")
    @Transactional
    fun updateProfileImage(
        authentication: Authentication,
        @RequestParam("file") file: MultipartFile
    ): UserProfile {

        val user = userRepository.findByEmail(authentication.name)!!
        val profile = profileRepository.findByUser(user)!!

        if (file.isEmpty) {
            throw RuntimeException("file missing")
        }

        val uploadsDir = File("/tmp/uploads")

        if (!uploadsDir.exists()) uploadsDir.mkdirs()

        val filename = "${UUID.randomUUID()}_${file.originalFilename}"
        val dest = File(uploadsDir, filename)

        file.transferTo(dest)

        profile.profileImageUrl = "/uploads/$filename"

        return profileRepository.save(profile)
    }





    @PostMapping("/profile/from-resume")
    @Transactional
    fun updateFromResume(
        authentication: Authentication,
        @RequestParam("file") file: MultipartFile
    ): UserProfile {

        val user = userRepository.findByEmail(authentication.name)
            ?: throw RuntimeException("User not found")

        val extracted = profileExtractor.extract(
            resumeTextExtractor.extract(file)
        )

        val profile = user.profile ?: UserProfile(
            user = user,
            name = extracted.name,
            major = extracted.major,
            gpa = extracted.gpa,
            preferredRole = extracted.preferredRole
        )

        profile.apply {
            name = extracted.name
            major = extracted.major
            gpa = extracted.gpa
            preferredRole = extracted.preferredRole
            if (location.isNullOrBlank() || location == "UNKNOWN") {
                if (extracted.location != "UNKNOWN") {
                    location = extracted.location
                }
            }
        }

        user.profile = profile
        userRepository.save(user)

        return profile
    }



}


data class ProfileUpdateRequest(
    val name: String?,
    val preferredRole: String?,
    val location: String?,
    val gpa: String?
)

