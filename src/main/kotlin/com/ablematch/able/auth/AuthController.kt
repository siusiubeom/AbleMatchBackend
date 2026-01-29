package com.ablematch.able.auth

import jakarta.transaction.Transactional
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider
) {


    @PostMapping("/signup")
    @Transactional
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<Any> {

        if (userRepository.existsByEmail(request.email)) {
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "이미 존재하는 이메일입니다."))
        }

        val user = User(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            role = "USER"
        )

        userRepository.save(user)

        return ResponseEntity.ok().build()
    }


    @PostMapping("/login")
    fun login(@RequestBody request: SignupRequest): TokenResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw RuntimeException("User not found")

        if (!passwordEncoder.matches(request.password, user.password))
            throw RuntimeException("Invalid password")

        val token = jwtProvider.createToken(user.email)
        return TokenResponse(token)
    }

    data class TokenResponse(
        val accessToken: String
    )

}

data class SignupRequest(
    val email: String,
    val password: String
)


