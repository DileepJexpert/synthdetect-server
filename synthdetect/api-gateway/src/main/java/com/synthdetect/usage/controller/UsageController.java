package com.synthdetect.usage.controller;

import com.synthdetect.common.model.ApiResponse;
import com.synthdetect.usage.model.UsageStat;
import com.synthdetect.usage.service.UsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/usage")
@RequiredArgsConstructor
@Tag(name = "Usage", description = "API usage and quota tracking")
public class UsageController {

    private final UsageService usageService;

    @GetMapping
    @Operation(summary = "Get current month usage and quota")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUsage(
            @AuthenticationPrincipal UUID userId) {
        Map<String, Object> usage = usageService.getCurrentUsage(userId);
        return ResponseEntity.ok(ApiResponse.success(usage));
    }

    @GetMapping("/history")
    @Operation(summary = "Get usage history by month")
    public ResponseEntity<ApiResponse<List<UsageStat>>> getUsageHistory(
            @AuthenticationPrincipal UUID userId) {
        List<UsageStat> history = usageService.getUsageHistory(userId);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
