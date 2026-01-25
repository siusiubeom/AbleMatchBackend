package com.ablematch.able.matching

import com.ablematch.able.auth.UserRepository
import com.ablematch.able.dto.MatchingWithCoursesDto
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/matching")
class MatchingController(
    private val matchingService: MatchingService,
    private val userRepository: UserRepository
) {

    @GetMapping("/with-courses")
    fun matchWithCourses(
        @AuthenticationPrincipal user: UserDetails
    ): List<MatchingWithCoursesDto> {

        val dbUser = userRepository.findByEmail(user.username)
            ?: throw RuntimeException("User not found")

        return matchingService.matchWithCourses(dbUser.id!!)
    }
}
