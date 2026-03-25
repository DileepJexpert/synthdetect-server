package com.synthdetect.webhook.service;

import com.synthdetect.common.exception.ApiException;
import com.synthdetect.common.util.HashUtil;
import com.synthdetect.detection.dto.DetectionResponse;
import com.synthdetect.user.model.User;
import com.synthdetect.user.service.UserService;
import com.synthdetect.webhook.model.Webhook;
import com.synthdetect.webhook.model.WebhookStatus;
import com.synthdetect.webhook.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final UserService userService;
    private final WebClient.Builder webClientBuilder;

    @Transactional
    public Webhook createWebhook(UUID userId, String url, String events, String description) {
        User user = userService.findActiveUser(userId);

        long existing = webhookRepository.findByUserId(userId).stream()
                .filter(w -> w.getStatus() == WebhookStatus.ACTIVE).count();
        if (existing >= 10) {
            throw new ApiException("Maximum of 10 active webhooks allowed", HttpStatus.BAD_REQUEST);
        }

        String secret = "whsec_" + UUID.randomUUID().toString().replace("-", "");

        Webhook webhook = Webhook.builder()
                .user(user)
                .url(url)
                .secret(secret)
                .events(events != null ? events : "detection.completed")
                .description(description)
                .build();

        return webhookRepository.save(webhook);
    }

    @Transactional(readOnly = true)
    public List<Webhook> listWebhooks(UUID userId) {
        return webhookRepository.findByUserId(userId);
    }

    @Transactional
    public void deleteWebhook(UUID userId, UUID webhookId) {
        Webhook webhook = webhookRepository.findByIdAndUserId(webhookId, userId)
                .orElseThrow(() -> new ApiException("Webhook not found", HttpStatus.NOT_FOUND));
        webhook.setStatus(WebhookStatus.INACTIVE);
        webhookRepository.save(webhook);
    }

    @Async
    public void deliverDetectionResult(String webhookUrl, DetectionResponse result) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        try {
            Map<String, Object> payload = Map.of(
                    "event", "detection.completed",
                    "timestamp", Instant.now().toString(),
                    "data", result
            );
            webClientBuilder.build()
                    .post()
                    .uri(webhookUrl)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Webhook delivered to {}", webhookUrl);
        } catch (Exception e) {
            log.warn("Webhook delivery failed to {}: {}", webhookUrl, e.getMessage());
        }
    }
}
