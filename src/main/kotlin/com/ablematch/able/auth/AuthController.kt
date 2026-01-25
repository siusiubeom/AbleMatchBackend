package com.ablematch.able.auth

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
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
}

data class SignupRequest(
    val email: String,
    val password: String
)

