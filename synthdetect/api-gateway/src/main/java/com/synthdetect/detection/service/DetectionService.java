package com.synthdetect.detection.service;

import com.synthdetect.auth.model.ApiKey;
import com.synthdetect.auth.repository.ApiKeyRepository;
import com.synthdetect.common.exception.ApiException;
import com.synthdetect.common.util.HashUtil;
import com.synthdetect.detection.client.MlEngineClient;
import com.synthdetect.detection.client.MlEngineClient.MlDetectionResult;
import com.synthdetect.detection.dto.*;
import com.synthdetect.detection.model.DetectionRequest;
import com.synthdetect.detection.model.DetectionSignal;
import com.synthdetect.detection.model.DetectionStatus;
import com.synthdetect.detection.model.DetectionType;
import com.synthdetect.detection.repository.DetectionRequestRepository;
import com.synthdetect.detection.repository.DetectionSignalRepository;
import com.synthdetect.usage.service.UsageService;
import com.synthdetect.user.model.User;
import com.synthdetect.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionService {

    private final DetectionRequestRepository requestRepository;
    private final DetectionSignalRepository signalRepository;
    private final MlEngineClient mlEngineClient;
    private final UsageService usageService;
    private final UserService userService;
    private final ApiKeyRepository apiKeyRepository;

    @Transactional
    public DetectionResponse detectImage(UUID userId, UUID apiKeyId, ImageDetectionRequest req) {
        User user = userService.findActiveUser(userId);
        usageService.checkAndIncrementQuota(userId, "image");

        DetectionRequest detection = DetectionRequest.builder()
                .user(user)
                .apiKey(resolveApiKey(apiKeyId))
                .type(DetectionType.IMAGE)
                .status(DetectionStatus.PROCESSING)
                .contentUrl(req.getImageUrl())
                .contentHash(req.getImageUrl() != null ? HashUtil.sha256(req.getImageUrl()) : null)
                .webhookUrl(req.getWebhookUrl())
                .jurisdiction(req.getJurisdiction() != null ? req.getJurisdiction() : "india_it_rules_2026")
                .build();

        detection = requestRepository.save(detection);

        return processDetection(detection, () -> mlEngineClient.detectImage(req.getImageUrl()), req.getFlagIfSynthetic());
    }

    @Transactional
    public DetectionResponse detectText(UUID userId, UUID apiKeyId, TextDetectionRequest req) {
        User user = userService.findActiveUser(userId);
        usageService.checkAndIncrementQuota(userId, "text");

        DetectionRequest detection = DetectionRequest.builder()
                .user(user)
                .apiKey(resolveApiKey(apiKeyId))
                .type(DetectionType.TEXT)
                .status(DetectionStatus.PROCESSING)
                .contentText(req.getText())
                .contentHash(req.getText() != null ? HashUtil.sha256(req.getText()) : null)
                .language(req.getLanguage())
                .webhookUrl(req.getWebhookUrl())
                .jurisdiction(req.getJurisdiction() != null ? req.getJurisdiction() : "india_it_rules_2026")
                .build();

        detection = requestRepository.save(detection);

        return processDetection(detection, () -> mlEngineClient.detectText(req.getText(), req.getLanguage()), req.getFlagIfSynthetic());
    }

    @Transactional
    public BatchDetectionResponse detectBatch(UUID userId, UUID apiKeyId, BatchDetectionRequest req) {
        User user = userService.findActiveUser(userId);

        List<DetectionResponse> results = new ArrayList<>();
        for (BatchDetectionRequest.BatchItem item : req.getItems()) {
            usageService.checkAndIncrementQuota(userId, item.getType());
            DetectionRequest detection = buildBatchItemRequest(user, apiKeyId, item, req.getJurisdiction(), req.getWebhookUrl());
            detection = requestRepository.save(detection);

            DetectionResponse result;
            if ("image".equalsIgnoreCase(item.getType())) {
                result = processDetection(detection, () -> mlEngineClient.detectImage(item.getImageUrl()), null);
            } else {
                result = processDetection(detection, () -> mlEngineClient.detectText(item.getText(), item.getLanguage()), null);
            }
            results.add(result);
        }

        return BatchDetectionResponse.builder()
                .batchId(UUID.randomUUID())
                .status("COMPLETED")
                .totalItems(req.getItems().size())
                .processedItems(results.size())
                .results(results)
                .createdAt(Instant.now())
                .build();
    }

    public DetectionResponse getResult(UUID userId, UUID requestId) {
        DetectionRequest detection = requestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new ApiException("Detection request not found", HttpStatus.NOT_FOUND));
        return toResponse(detection, signalRepository.findByRequestId(requestId));
    }

    public Page<DetectionResponse> listResults(UUID userId, Pageable pageable) {
        return requestRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(d -> toResponse(d, List.of()));
    }

    // --- private helpers ---

    private DetectionResponse processDetection(DetectionRequest detection,
                                                java.util.function.Supplier<MlDetectionResult> mlCall,
                                                Boolean flagIfSynthetic) {
        try {
            MlDetectionResult ml = mlCall.get();

            detection.setStatus(DetectionStatus.COMPLETED);
            detection.setIsSynthetic(ml.isSynthetic());
            detection.setConfidenceScore(BigDecimal.valueOf(ml.confidenceScore()));
            detection.setModelVersion(ml.modelVersion());
            detection.setProcessingMs(ml.processingMs());
            detection.setCompletedAt(Instant.now());

            if (Boolean.TRUE.equals(flagIfSynthetic) && ml.isSynthetic()) {
                detection.setFlaggedForReview(true);
            }

            detection = requestRepository.save(detection);

            List<DetectionSignal> signals = new ArrayList<>();
            if (ml.signals() != null) {
                for (var s : ml.signals()) {
                    signals.add(signalRepository.save(DetectionSignal.builder()
                            .request(detection)
                            .signalName(s.name())
                            .signalValue(BigDecimal.valueOf(s.value()))
                            .signalWeight(BigDecimal.valueOf(s.weight()))
                            .description(s.description())
                            .build()));
                }
            }

            return toResponse(detection, signals);

        } catch (MlEngineClient.MlEngineException e) {
            detection.setStatus(DetectionStatus.FAILED);
            detection.setErrorMessage(e.getMessage());
            detection.setCompletedAt(Instant.now());
            requestRepository.save(detection);
            throw new ApiException("Detection failed: ML engine unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private DetectionRequest buildBatchItemRequest(User user, UUID apiKeyId,
                                                    BatchDetectionRequest.BatchItem item,
                                                    String jurisdiction, String webhookUrl) {
        DetectionType type = "image".equalsIgnoreCase(item.getType()) ? DetectionType.IMAGE : DetectionType.TEXT;
        return DetectionRequest.builder()
                .user(user)
                .apiKey(resolveApiKey(apiKeyId))
                .type(type)
                .status(DetectionStatus.PROCESSING)
                .contentUrl(item.getImageUrl())
                .contentText(item.getText())
                .language(item.getLanguage())
                .webhookUrl(webhookUrl)
                .jurisdiction(jurisdiction != null ? jurisdiction : "india_it_rules_2026")
                .build();
    }

    private ApiKey resolveApiKey(UUID apiKeyId) {
        if (apiKeyId == null) return null;
        return apiKeyRepository.findById(apiKeyId).orElse(null);
    }

    private DetectionResponse toResponse(DetectionRequest d, List<DetectionSignal> signals) {
        String verdict = null;
        if (d.getIsSynthetic() != null) {
            if (d.getIsSynthetic()) {
                verdict = d.getConfidenceScore() != null && d.getConfidenceScore().doubleValue() >= 0.8
                        ? "synthetic" : "inconclusive";
            } else {
                verdict = "authentic";
            }
        }

        List<DetectionResponse.SignalDto> signalDtos = signals.stream()
                .map(s -> DetectionResponse.SignalDto.builder()
                        .name(s.getSignalName())
                        .value(s.getSignalValue())
                        .weight(s.getSignalWeight())
                        .description(s.getDescription())
                        .build())
                .toList();

        return DetectionResponse.builder()
                .requestId(d.getId())
                .status(d.getStatus().name())
                .type(d.getType().name())
                .isSynthetic(d.getIsSynthetic())
                .confidenceScore(d.getConfidenceScore())
                .verdict(verdict)
                .modelVersion(d.getModelVersion())
                .processingMs(d.getProcessingMs())
                .signals(signalDtos.isEmpty() ? null : signalDtos)
                .flaggedForReview(d.getFlaggedForReview())
                .jurisdiction(d.getJurisdiction())
                .createdAt(d.getCreatedAt())
                .completedAt(d.getCompletedAt())
                .build();
    }
}
