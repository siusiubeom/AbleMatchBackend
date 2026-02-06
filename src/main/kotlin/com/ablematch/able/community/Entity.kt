package com.ablematch.able.community

import com.ablematch.able.auth.User
import com.ablematch.able.auth.UserRepository
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Entity
class CommunityPost(
    @Id @GeneratedValue
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    var user: User,

    @Column(columnDefinition = "TEXT")
    var content: String,

    var createdAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL])
    var comments: MutableList<Comment> = mutableListOf(),

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL])
    var likes: MutableList<PostLike> = mutableListOf()
)

@Entity
class Comment(
    @Id @GeneratedValue
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    var post: CommunityPost,

    @ManyToOne(fetch = FetchType.LAZY)
    var user: User,

    var content: String,
    var createdAt: Instant = Instant.now()
)

@Entity
class PostLike(
    @Id @GeneratedValue
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    var post: CommunityPost,

    @ManyToOne(fetch = FetchType.LAZY)
    var user: User
)

interface CommunityPostRepository : JpaRepository<CommunityPost, UUID>
interface CommentRepository : JpaRepository<Comment, UUID>
interface PostLikeRepository : JpaRepository<PostLike, UUID>

data class FeedPostDto(
    val id: UUID,
    val author: String,
    val content: String,
    val likeCount: Int,
    val commentCount: Int,
    val createdAt: Instant
)

@RestController
@RequestMapping("/api/community")
class CommunityController(
    private val postRepo: CommunityPostRepository,
    private val userRepo: UserRepository
) {

    @PostMapping("/post")
    fun createPost(
        @AuthenticationPrincipal user: UserDetails,
        @RequestBody body: Map<String, String>
    ): CommunityPost {
        val dbUser = userRepo.findByEmail(user.username)!!
        return postRepo.save(
            CommunityPost(
                user = dbUser,
                content = body["content"]!!
            )
        )
    }

    @GetMapping("/feed")
    @Transactional
    fun feed(): List<FeedPostDto> =
        postRepo.findAll()
            .sortedByDescending { it.createdAt }
            .map {
                FeedPostDto(
                    id = it.id!!,
                    author = it.user.email,
                    content = it.content,
                    likeCount = it.likes.size,
                    commentCount = it.comments.size,
                    createdAt = it.createdAt
                )
            }
}
