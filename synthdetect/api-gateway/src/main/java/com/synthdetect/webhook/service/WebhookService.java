package com.synthdetect.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synthdetect.common.exception.ApiException;
import com.synthdetect.detection.dto.DetectionResponse;
import com.synthdetect.user.model.User;
import com.synthdetect.user.service.UserService;
import com.synthdetect.webhook.model.Webhook;
import com.synthdetect.webhook.model.WebhookDelivery;
import com.synthdetect.webhook.model.WebhookStatus;
import com.synthdetect.webhook.repository.WebhookDeliveryRepository;
import com.synthdetect.webhook.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final UserService userService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Transactional
    public Webhook createWebhook(UUID userId, String url, String events, String description) {
        User user = userService.findActiveUser(userId);

        long existing = webhookRepository.findByUserId(userId).stream()
                .filter(w -> w.getStatus() == WebhookStatus.ACTIVE).count();
        if (existing >= 10) {
            throw new ApiException("MAX_WEBHOOKS", "Maximum of 10 active webhooks allowed.", HttpStatus.BAD_REQUEST);
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
                .orElseThrow(() -> new ApiException("WEBHOOK_NOT_FOUND", "Webhook not found.", HttpStatus.NOT_FOUND));
        webhook.setStatus(WebhookStatus.INACTIVE);
        webhookRepository.save(webhook);
    }

    /**
     * Delivers a detection.completed event to the given URL.
     * Signs payload with HMAC-SHA256 using the webhook secret.
     * Persists delivery log. Falls back gracefully on failure.
     */
    @Async
    public void deliverDetectionResult(String webhookUrl, DetectionResponse result) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        // Find the webhook by URL to get the signing secret
        webhookRepository.findAll().stream()
                .filter(w -> webhookUrl.equals(w.getUrl()) && w.getStatus() == WebhookStatus.ACTIVE)
                .findFirst()
                .ifPresentOrElse(
                        webhook -> deliverToWebhook(webhook, "detection.completed", result, result.getRequestId()),
                        () -> deliverUnsigned(webhookUrl, "detection.completed", result)
                );
    }

    @Async
    public void deliverQuotaEvent(UUID userId, String eventType, Map<String, Object> data) {
        webhookRepository.findByUserIdAndStatus(userId, WebhookStatus.ACTIVE).stream()
                .filter(w -> w.getEvents().contains(eventType))
                .forEach(webhook -> deliverToWebhook(webhook, eventType, data, null));
    }

    private void deliverToWebhook(Webhook webhook, String eventType, Object data, UUID requestId) {
        Instant start = Instant.now();
        String payloadJson;
        try {
            Map<String, Object> envelope = Map.of(
                    "id", UUID.randomUUID().toString(),
                    "event", eventType,
                    "timestamp", Instant.now().toString(),
                    "data", data
            );
            payloadJson = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload: {}", e.getMessage());
            return;
        }

        String signature = computeHmacSignature(webhook.getSecret(), payloadJson);

        Integer statusCode = null;
        String responseBody = null;
        boolean success = false;

        try {
            var response = webClientBuilder.build()
                    .post()
                    .uri(webhook.getUrl())
                    .header("Content-Type", "application/json")
                    .header("X-SynthDetect-Signature", "sha256=" + signature)
                    .header("X-SynthDetect-Event", eventType)
                    .header("X-SynthDetect-Delivery", UUID.randomUUID().toString())
                    .bodyValue(payloadJson)
                    .retrieve()
                    .toEntity(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null) {
                statusCode = response.getStatusCode().value();
                responseBody = response.getBody();
                success = response.getStatusCode().is2xxSuccessful();
            }
        } catch (WebClientResponseException e) {
            statusCode = e.getStatusCode().value();
            responseBody = e.getResponseBodyAsString();
            log.warn("Webhook delivery failed webhook={} status={}", webhook.getId(), statusCode);
        } catch (Exception e) {
            log.warn("Webhook delivery error webhook={}: {}", webhook.getId(), e.getMessage());
        }

        int durationMs = (int) Duration.between(start, Instant.now()).toMillis();

        // Persist delivery log
        persistDelivery(webhook, requestId, eventType, payloadJson, statusCode, responseBody, durationMs, success);

        // Update webhook health counters
        updateWebhookHealth(webhook, success);
    }

    private void deliverUnsigned(String url, String eventType, Object data) {
        try {
            Map<String, Object> envelope = Map.of(
                    "event", eventType,
                    "timestamp", Instant.now().toString(),
                    "data", data
            );
            webClientBuilder.build()
                    .post()
                    .uri(url)
                    .bodyValue(envelope)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception e) {
            log.warn("Unsigned webhook delivery failed url={}: {}", url, e.getMessage());
        }
    }

    private void persistDelivery(Webhook webhook, UUID requestId, String eventType,
                                  String payload, Integer status, String responseBody,
                                  int durationMs, boolean success) {
        try {
            WebhookDelivery delivery = WebhookDelivery.builder()
                    .webhook(webhook)
                    .eventType(eventType)
                    .payload(payload)
                    .responseStatus(status)
                    .responseBody(responseBody != null && responseBody.length() > 500
                            ? responseBody.substring(0, 500) : responseBody)
                    .durationMs(durationMs)
                    .success(success)
                    .build();
            deliveryRepository.save(delivery);
        } catch (Exception e) {
            log.warn("Failed to persist webhook delivery log: {}", e.getMessage());
        }
    }

    @Transactional
    protected void updateWebhookHealth(Webhook webhook, boolean success) {
        try {
            Webhook managed = webhookRepository.findById(webhook.getId()).orElse(null);
            if (managed == null) return;
            if (success) {
                managed.setLastSuccessAt(Instant.now());
                managed.setConsecutiveFailures(0);
            } else {
                managed.setLastFailureAt(Instant.now());
                managed.setConsecutiveFailures(managed.getConsecutiveFailures() + 1);
                if (managed.getConsecutiveFailures() >= 10) {
                    managed.setStatus(WebhookStatus.FAILED);
                    log.warn("Webhook auto-disabled after 10 consecutive failures webhook={}", managed.getId());
                }
            }
            webhookRepository.save(managed);
        } catch (Exception e) {
            log.warn("Failed to update webhook health: {}", e.getMessage());
        }
    }

    private String computeHmacSignature(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("HMAC signing failed: {}", e.getMessage());
            return "";
        }
    }
}
