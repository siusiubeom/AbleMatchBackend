package com.ablematch.able.auth

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
    fun signup(@RequestBody request: SignupRequest): User {
        val user = User(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            role = "USER"
        )
        return userRepository.save(user)
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


