package com.synthdetect.webhook.controller;

import com.synthdetect.common.model.ApiResponse;
import com.synthdetect.webhook.model.Webhook;
import com.synthdetect.webhook.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Webhook registration and management")
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping
    @Operation(summary = "Register a new webhook endpoint")
    public ResponseEntity<ApiResponse<Webhook>> createWebhook(
            @AuthenticationPrincipal UUID userId,
            @RequestBody Map<String, String> body) {
        Webhook webhook = webhookService.createWebhook(
                userId,
                body.get("url"),
                body.get("events"),
                body.get("description"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(webhook));
    }

    @GetMapping
    @Operation(summary = "List all registered webhooks")
    public ResponseEntity<ApiResponse<List<Webhook>>> listWebhooks(
            @AuthenticationPrincipal UUID userId) {
        List<Webhook> webhooks = webhookService.listWebhooks(userId);
        return ResponseEntity.ok(ApiResponse.success(webhooks));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a webhook")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteWebhook(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        webhookService.deleteWebhook(userId, id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "deactivated")));
    }
}
