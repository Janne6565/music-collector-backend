package com.rekordo.security;

import com.rekordo.model.core.TokenType;
import com.rekordo.repository.UserRepository;
import com.rekordo.services.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves {@code Authorization: Bearer} into an authenticated principal.
 *
 * Only an ACCESS token is accepted, and only when its {@code tokenVersion} still matches
 * the user's — so bumping that column signs every device out at once.
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            jwtService
                    .parse(header.substring(BEARER.length()), TokenType.ACCESS)
                    .flatMap(parsed -> userRepository
                            .findById(parsed.userId())
                            .filter(user -> user.getTokenVersion() == parsed.tokenVersion()))
                    .ifPresent(user ->
                            SecurityContextHolder.getContext().setAuthentication(new AuthenticationToken(user)));
        }
        chain.doFilter(request, response);
    }
}
