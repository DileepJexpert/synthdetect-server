package com.synthdetect.auth.filter;

import com.synthdetect.config.JwtService;
import com.synthdetect.user.model.UserRole;
import com.synthdetect.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final com.synthdetect.auth.service.TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Only process if no authentication set yet (ApiKeyAuthFilter didn't handle it)
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ") && !authHeader.startsWith("Bearer sd_")) {
            String token = authHeader.substring(7);
            try {
                if (jwtService.isTokenValid(token)) {
                    String jti = jwtService.extractJti(token);
                    if (tokenBlacklistService.isBlacklisted(jti)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    UUID userId = jwtService.extractUserId(token);

                    // Look up the user role to assign correct Spring authority
                    List<SimpleGrantedAuthority> authorities = userRepository.findById(userId)
                            .map(u -> u.getRole() == UserRole.ADMIN
                                    ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                                              new SimpleGrantedAuthority("ROLE_USER"))
                                    : List.of(new SimpleGrantedAuthority("ROLE_USER")))
                            .orElse(List.of(new SimpleGrantedAuthority("ROLE_USER")));

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                log.debug("JWT authentication failed: {}", e.getMessage());
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
