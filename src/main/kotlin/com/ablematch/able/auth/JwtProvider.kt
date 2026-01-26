package com.ablematch.able.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Date

@Component
class JwtProvider(
    @Value("\${jwt.secret}") private val secret: String
) {
    private val validity = 1000L * 60 * 60 * 24

    private val key = Keys.hmacShaKeyFor(
        Decoders.BASE64.decode(secret)
    )

    fun createToken(email: String): String {
        val claims = Jwts.claims().setSubject(email)
        val now = Date()
        val exp = Date(now.time + validity)

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(exp)
            .signWith(key)
            .compact()
    }

    fun getEmail(token: String): String =
        Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body.subject
}

@Component
class JwtFilter(
    private val jwtProvider: JwtProvider,
    private val userService: UserService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        req: HttpServletRequest,
        res: HttpServletResponse,
        chain: FilterChain
    ) {
        val header = req.getHeader("Authorization")

        if (header?.startsWith("Bearer ") == true) {
            val token = header.substring(7)

            try {
                val email = jwtProvider.getEmail(token)
                val user = userService.loadUserByUsername(email)
                val auth = UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    user.authorities
                )
                SecurityContextHolder.getContext().authentication = auth
            } catch (e: Exception) {
                SecurityContextHolder.clearContext()
            }
        }

        chain.doFilter(req, res)
    }

}

