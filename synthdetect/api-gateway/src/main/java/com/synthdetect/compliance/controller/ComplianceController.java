package com.synthdetect.compliance.controller;

import com.synthdetect.common.model.ApiResponse;
import com.synthdetect.compliance.dto.TakedownRequest;
import com.synthdetect.compliance.model.ComplianceReport;
import com.synthdetect.compliance.service.ComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance", description = "India IT Rules 2026 compliance and takedown management")
public class ComplianceController {

    private final ComplianceService complianceService;

    @PostMapping("/takedown")
    @Operation(summary = "Submit a takedown request for synthetic content")
    public ResponseEntity<ApiResponse<ComplianceReport>> submitTakedown(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody TakedownRequest request) {
        ComplianceReport report = complianceService.submitTakedown(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(report));
    }

    @GetMapping
    @Operation(summary = "List all compliance reports")
    public ResponseEntity<ApiResponse<Page<ComplianceReport>>> listReports(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ComplianceReport> reports = complianceService.listReports(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get compliance summary stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            @AuthenticationPrincipal UUID userId) {
        Map<String, Object> summary = complianceService.getComplianceSummary(userId);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific compliance report")
    public ResponseEntity<ApiResponse<ComplianceReport>> getReport(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        ComplianceReport report = complianceService.getReport(userId, id);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Resolve a compliance report (TAKEDOWN_COMPLETED / CLEARED)")
    public ResponseEntity<ApiResponse<ComplianceReport>> resolveReport(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        ComplianceReport report = complianceService.resolveReport(userId, id, body.get("action"), body.get("notes"));
        return ResponseEntity.ok(ApiResponse.success(report));
    }
}
