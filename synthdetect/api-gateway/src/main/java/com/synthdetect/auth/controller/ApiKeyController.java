package com.synthdetect.auth.controller;

import com.synthdetect.auth.service.ApiKeyService;
import com.synthdetect.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createKey(
            @AuthenticationPrincipal UUID userId,
            @RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "Default");
        String environment = (String) body.getOrDefault("environment", "live");

        @SuppressWarnings("unchecked")
        List<String> scopesList = (List<String>) body.get("scopes");
        String[] scopes = scopesList != null ? scopesList.toArray(new String[0]) : null;

        Map<String, Object> result = apiKeyService.createApiKey(userId, name, scopes, environment);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listKeys(
            @AuthenticationPrincipal UUID userId) {
        List<Map<String, Object>> keys = apiKeyService.listApiKeys(userId);
        return ResponseEntity.ok(ApiResponse.success(keys));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> revokeKey(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        apiKeyService.revokeApiKey(userId, id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "revoked")));
    }
}
