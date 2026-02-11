package com.ablematch.able.community

import com.ablematch.able.auth.User
import com.ablematch.able.auth.UserRepository
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File
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

    @ElementCollection
    var imageUrls: MutableList<String> = mutableListOf(),

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true)
    var comments: MutableList<Comment> = mutableListOf(),

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true)
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
    var createdAt: Instant = Instant.now(),
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

interface CommunityPostRepository : JpaRepository<CommunityPost, UUID> {

    @EntityGraph(attributePaths = [
        "user",
        "user.profile",
        "likes",
        "comments"
    ])
    fun findAllByOrderByCreatedAtDesc(): List<CommunityPost>
    @EntityGraph(attributePaths = [
        "user",
        "user.profile",
        "comments",
        "comments.user",
        "comments.user.profile"
    ])
    fun findEntityGraphById(id: UUID): CommunityPost?
}
interface CommentRepository : JpaRepository<Comment, UUID>
interface PostLikeRepository : JpaRepository<PostLike, UUID> {
    fun existsByPostIdAndUserId(postId: UUID, userId: UUID): Boolean
    fun findByPostIdAndUserId(postId: UUID, userId: UUID): PostLike?
    fun findByUserId(userId: UUID): List<PostLike>
}



@RestController
@RequestMapping("/api/community")
class CommunityController(
    private val postRepo: CommunityPostRepository,
    private val userRepo: UserRepository,
    private val commentRepo: CommentRepository,
    private val likeRepo: PostLikeRepository,
) {

    @PostMapping("/post")
    fun createPost(
        @AuthenticationPrincipal user: UserDetails,
        @RequestBody body: CreatePostRequest
    ): CommunityPost {

        val dbUser = userRepo.findByEmail(user.username)!!

        return postRepo.save(
            CommunityPost(
                user = dbUser,
                content = body.content,
                imageUrls = body.imageUrls.toMutableList()
            )
        )
    }


    data class CreateCommentResponse(
        val id: UUID,
        val content: String,
        val createdAt: Instant
    )

    @PostMapping("/{postId}/comment")
    fun createComment(
        @PathVariable postId: UUID,
        @AuthenticationPrincipal user: UserDetails,
        @RequestBody body: Map<String, String>
    ): CreateCommentResponse {
        val dbUser = userRepo.findByEmail(user.username)!!
        val post = postRepo.findEntityGraphById(postId)
            ?: throw NoSuchElementException("post not found")

        val saved = commentRepo.save(
            Comment(
                post = post,
                user = dbUser,
                content = body["content"]!!
            )
        )

        return CreateCommentResponse(
            id = saved.id!!,
            content = saved.content,
            createdAt = saved.createdAt
        )
    }


    @GetMapping("/{postId}/comments")
    @Transactional
    fun comments(
        @PathVariable postId: UUID,
        @AuthenticationPrincipal user: UserDetails?
    ): List<CommentDto> {

        val currentEmail = user?.username
        val post = postRepo.findEntityGraphById(postId)
            ?: throw NoSuchElementException("post not found")
        val postAuthorEmail = post.user.email

        val aliasMap = linkedMapOf<String, String>()
        var counter = 1

        return post.comments.sortedBy { it.createdAt }.map {
            val email = it.user.email

            val alias = if (email == postAuthorEmail) {
                "익명"
            } else {
                aliasMap.getOrPut(email) { "익명 ${counter++}" }
            }

            CommentDto(
                id = it.id!!,
                authorAlias = alias,
                content = it.content,
                createdAt = it.createdAt,
                isPostAuthor = email == postAuthorEmail,
                isOwner = email == currentEmail
            )
        }
    }


    @DeleteMapping("/post/{postId}")
    fun deletePost(
        @PathVariable postId: UUID,
        @AuthenticationPrincipal user: UserDetails
    ) {
        val dbUser = userRepo.findByEmail(user.username)!!
        val post = postRepo.findById(postId).orElseThrow()

        if (post.user.id != dbUser.id) {
            throw RuntimeException("Not allowed")
        }

        postRepo.delete(post)
    }

    @DeleteMapping("/comment/{commentId}")
    fun deleteComment(
        @PathVariable commentId: UUID,
        @AuthenticationPrincipal user: UserDetails
    ) {
        val dbUser = userRepo.findByEmail(user.username)!!
        val comment = commentRepo.findById(commentId).orElseThrow()

        if (comment.user.id != dbUser.id) {
            throw RuntimeException("Not allowed")
        }

        commentRepo.delete(comment)
    }



    @GetMapping("/feed")
    @Transactional
    fun feed(@AuthenticationPrincipal user: UserDetails?): List<FeedPostDto> {

        val currentUser = user?.let { userRepo.findByEmail(it.username) }

        val likedPostIds: Set<UUID> =
            currentUser?.id?.let { uid ->
                likeRepo.findByUserId(uid)
                    .mapNotNull { it.post.id }
                    .toSet()
            } ?: emptySet()

        return postRepo.findAllByOrderByCreatedAtDesc()
            .map { post ->

                val profile = post.user.profile

                FeedPostDto(
                    id = post.id!!,
                    authorName = profile?.name ?: post.user.email,
                    authorEmail = post.user.email,
                    authorProfileImage = profile?.profileImageUrl,
                    content = post.content,
                    imageUrls = post.imageUrls,
                    likeCount = post.likes.size,
                    commentCount = post.comments.size,
                    createdAt = post.createdAt,
                    isOwner = currentUser?.email == post.user.email,
                    isLikedByMe = post.id in likedPostIds
                )
            }
    }


    @PostMapping("/{postId}/like")
    fun toggleLike(
        @PathVariable postId: UUID,
        @AuthenticationPrincipal user: UserDetails
    ) {
        val dbUser = userRepo.findByEmail(user.username)!!
        val post = postRepo.findById(postId).orElseThrow()

        val existing = likeRepo.findByPostIdAndUserId(postId, dbUser.id!!)

        if (existing != null) {
            likeRepo.delete(existing)
        } else {
            likeRepo.save(PostLike(post = post, user = dbUser))
        }
    }

    @PostMapping("/upload")
    fun uploadImage(
        @RequestParam("file") file: MultipartFile
    ): String {

        val uploadsDir = File("/tmp/uploads")
        if (!uploadsDir.exists()) uploadsDir.mkdirs()

        val filename = "${UUID.randomUUID()}_${file.originalFilename}"
        val dest = File(uploadsDir, filename)

        file.transferTo(dest)

        return "/uploads/$filename"
    }




}

data class CommentDto(
    val id: UUID,
    val authorAlias: String,
    val content: String,
    val createdAt: Instant,
    val isPostAuthor: Boolean,
    val isOwner: Boolean
)

data class FeedPostDto(
    val id: UUID,
    val authorName: String,
    val authorEmail: String,
    val authorProfileImage: String?,
    val content: String,
    val imageUrls: List<String>,
    val likeCount: Int,
    val commentCount: Int,
    val createdAt: Instant,
    val isOwner: Boolean,
    val isLikedByMe: Boolean
)

data class CreatePostRequest(
    val content: String,
    val imageUrls: List<String> = emptyList()
)
