package com.synthdetect.detection.controller;

import com.synthdetect.common.model.ApiResponse;
import com.synthdetect.detection.dto.*;
import com.synthdetect.detection.service.DetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/detect")
@RequiredArgsConstructor
@Tag(name = "Detection", description = "AI/Synthetic content detection endpoints")
public class DetectionController {

    private final DetectionService detectionService;

    @PostMapping("/image")
    @Operation(summary = "Detect synthetic content in an image")
    public ResponseEntity<ApiResponse<DetectionResponse>> detectImage(
            @AuthenticationPrincipal UUID userId,
            @RequestAttribute(value = "apiKeyId", required = false) UUID apiKeyId,
            @Valid @RequestBody ImageDetectionRequest request) {
        DetectionResponse response = detectionService.detectImage(userId, apiKeyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/text")
    @Operation(summary = "Detect AI-generated text")
    public ResponseEntity<ApiResponse<DetectionResponse>> detectText(
            @AuthenticationPrincipal UUID userId,
            @RequestAttribute(value = "apiKeyId", required = false) UUID apiKeyId,
            @Valid @RequestBody TextDetectionRequest request) {
        DetectionResponse response = detectionService.detectText(userId, apiKeyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/batch")
    @Operation(summary = "Batch detection (up to 50 items)")
    public ResponseEntity<ApiResponse<BatchDetectionResponse>> detectBatch(
            @AuthenticationPrincipal UUID userId,
            @RequestAttribute(value = "apiKeyId", required = false) UUID apiKeyId,
            @Valid @RequestBody BatchDetectionRequest request) {
        BatchDetectionResponse response = detectionService.detectBatch(userId, apiKeyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "Get detection result by ID")
    public ResponseEntity<ApiResponse<DetectionResponse>> getResult(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID requestId) {
        DetectionResponse response = detectionService.getResult(userId, requestId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/history")
    @Operation(summary = "List past detection requests")
    public ResponseEntity<ApiResponse<Page<DetectionResponse>>> listHistory(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<DetectionResponse> results = detectionService.listResults(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
