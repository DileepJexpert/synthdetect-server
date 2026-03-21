package com.synthdetect.auth.filter;

import com.synthdetect.auth.model.ApiKey;
import com.synthdetect.auth.repository.ApiKeyRepository;
import com.synthdetect.auth.service.ApiKeyAuthenticationService;
import com.synthdetect.common.exception.InvalidApiKeyException;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyAuthenticationService apiKeyAuthService;
    private final ApiKeyRepository apiKeyRepository;

    @Override
    @Transactional
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer sd_")) {
            String apiKeyValue = authHeader.substring(7);
            try {
                ApiKey apiKey = apiKeyAuthService.authenticate(apiKeyValue);

                List<SimpleGrantedAuthority> authorities = Arrays.stream(apiKey.getScopesArray())
                        .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.trim().toUpperCase()))
                        .toList();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                apiKey.getUser().getId(), // principal = userId
                                apiKey.getId(),           // credentials = apiKeyId
                                authorities
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Record usage directly via repository to avoid circular dependency
                apiKeyRepository.updateUsage(apiKey.getId(), Instant.now());
            } catch (InvalidApiKeyException e) {
                log.debug("API key authentication failed: {}", e.getMessage());
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
