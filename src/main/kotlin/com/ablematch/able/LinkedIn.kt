package com.ablematch.able.publicprofile

import com.ablematch.able.auth.User
import com.ablematch.able.auth.UserRepository
import com.ablematch.able.auth.UserProfileRepository
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File
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

    @Column var headline: String? = null,
    @Column(length = 5000) var bio: String? = null,
    @Column var portfolioUrl: String? = null,
    @Column var githubUrl: String? = null,
    @Column var linkedinUrl: String? = null,
    @Column var skills: String? = null,
    @Column var isPublic: Boolean = true
)
@Entity
@Table(name = "user_experience")
class UserExperience(
    @Id @GeneratedValue
    var id: UUID? = null,

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnore
    var user: User,

    @Column(nullable = false) var company: String,
    @Column(nullable = false) var title: String,
    @Column var location: String? = null,
    @Column var startDate: String? = null,
    @Column var endDate: String? = null,
    @Column(length = 3000) var description: String? = null,
    @Column var imageUrl: String? = null
)



interface UserPublicProfileRepository : JpaRepository<UserPublicProfile, UUID> {
    fun findByUser(user: User): UserPublicProfile?
    fun findAllByIsPublicTrue(): List<UserPublicProfile>
}

interface UserExperienceRepository : JpaRepository<UserExperience, UUID> {
    fun findAllByUserOrderByStartDateDesc(user: User): List<UserExperience>
}

data class ExperienceView(
    val id: UUID,
    val company: String,
    val title: String,
    val location: String?,
    val startDate: String?,
    val endDate: String?,
    val description: String?,
    val imageUrl: String?
)

data class PublicProfileView(
    val userId: UUID,
    val name: String,
    val headline: String?,
    val bio: String?,
    val profileImageUrl: String?,
    val skills: String?,
    val experiences: List<ExperienceView>
)



data class PublicProfileUpdateRequest(
    val headline: String?,
    val bio: String?,
    val portfolioUrl: String?,
    val githubUrl: String?,
    val linkedinUrl: String?,
    val skills: String?
)

@RestController
@RequestMapping("/api/public-profile")
class PublicProfileViewController(
    private val userRepository: UserRepository,
    private val profileRepo: UserProfileRepository,
    private val publicRepo: UserPublicProfileRepository,
    private val expRepo: UserExperienceRepository
) {



    @GetMapping("/{userId}")
    fun getPublicProfile(@PathVariable userId: UUID): PublicProfileView {
        val user = userRepository.findById(userId).orElseThrow()
        val base = profileRepo.findByUser(user)!!
        val public = publicRepo.findByUser(user)

        val experiences = expRepo
            .findAllByUserOrderByStartDateDesc(user)
            .map {
                ExperienceView(
                    id = it.id!!,
                    company = it.company,
                    title = it.title,
                    location = it.location,
                    startDate = it.startDate,
                    endDate = it.endDate,
                    description = it.description,
                    imageUrl = it.imageUrl
                )
            }

        return PublicProfileView(
            userId = user.id!!,
            name = base.name,
            headline = public?.headline ?: base.preferredRole,
            bio = public?.bio,
            profileImageUrl = base.profileImageUrl,
            skills = public?.skills,
            experiences = experiences
        )
    }

    @GetMapping("/list")
    fun listPublicProfiles(): List<PublicProfileView> {
        return publicRepo.findAllByIsPublicTrue().take(10).map { public ->

            val base = profileRepo.findByUser(public.user)!!

            PublicProfileView(
                userId = public.user.id!!,
                name = base.name,
                headline = public.headline ?: base.preferredRole,
                bio = public.bio,
                profileImageUrl = base.profileImageUrl,
                skills = public.skills,
                experiences = emptyList()
            )
        }
    }
}




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

@RestController
@RequestMapping("/api/me/experience")
class UserExperienceController(
    private val userRepository: UserRepository,
    private val expRepo: UserExperienceRepository
) {

    @GetMapping
    fun list(auth: Authentication): List<UserExperience> {
        val user = userRepository.findByEmail(auth.name)
            ?: throw RuntimeException("User not found")

        return expRepo.findAllByUserOrderByStartDateDesc(user)
    }

    @PostMapping("/{id}/image")
    @Transactional
    fun uploadImage(
        auth: Authentication,
        @PathVariable id: UUID,
        @RequestParam("file") file: MultipartFile
    ): UserExperience {

        val user = userRepository.findByEmail(auth.name)
            ?: throw RuntimeException("User not found")

        val exp = expRepo.findById(id).orElseThrow()

        if (exp.user.id != user.id) {
            throw RuntimeException("Forbidden")
        }

        val uploadsDir = File("/tmp/uploads")
        if (!uploadsDir.exists()) uploadsDir.mkdirs()

        val filename = "${UUID.randomUUID()}_${file.originalFilename}"
        val dest = File(uploadsDir, filename)

        file.transferTo(dest)

        exp.imageUrl = "/uploads/$filename"

        return expRepo.save(exp)
    }


    @PostMapping
    fun create(
        auth: Authentication,
        @RequestBody req: ExperienceRequest
    ): UserExperience {

        val user = userRepository.findByEmail(auth.name)
            ?: throw RuntimeException("User not found")

        val exp = UserExperience(
            user = user,
            company = req.company,
            title = req.title,
            location = req.location,
            startDate = req.startDate,
            endDate = req.endDate,
            description = req.description
        )

        return expRepo.save(exp)
    }

    @DeleteMapping("/{id}")
    fun delete(
        auth: Authentication,
        @PathVariable id: UUID
    ) {
        val user = userRepository.findByEmail(auth.name)
            ?: throw RuntimeException("User not found")

        val exp = expRepo.findById(id).orElseThrow()

        if (exp.user.id != user.id) {
            throw RuntimeException("Forbidden")
        }

        expRepo.delete(exp)
    }
}

data class ExperienceRequest(
    val company: String,
    val title: String,
    val location: String?,
    val startDate: String?,
    val endDate: String?,
    val description: String?
)

