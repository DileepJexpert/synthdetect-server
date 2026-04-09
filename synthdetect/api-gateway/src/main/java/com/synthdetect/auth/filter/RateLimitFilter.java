package com.synthdetect.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synthdetect.common.exception.RateLimitExceededException;
import com.synthdetect.common.model.ApiResponse;
import com.synthdetect.config.RateLimitConfig;
import com.synthdetect.user.model.User;
import com.synthdetect.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Enforces per-user rate limits after authentication has been resolved.
 * Must run AFTER both ApiKeyAuthFilter and JwtAuthFilter.
 * Reads the user's rateLimitRpm from DB (cached via Spring's first-level cache).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitConfig rateLimitConfig;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UUID userId) {
            int rpmLimit = userRepository.findById(userId)
                    .map(User::getRateLimitRpm)
                    .orElse(60);
            try {
                rateLimitConfig.checkRateLimit(userId, rpmLimit);
            } catch (RateLimitExceededException e) {
                log.warn("Rate limit exceeded userId={} limit={}", userId, rpmLimit);
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write(objectMapper.writeValueAsString(
                        ApiResponse.error("RATE_LIMIT_EXCEEDED", e.getMessage())));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/auth/") ||
               path.startsWith("/actuator/") ||
               path.equals("/swagger-ui.html") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs");
    }
}
