package com.synthdetect.compliance.service;

import com.synthdetect.common.exception.ApiException;
import com.synthdetect.compliance.dto.TakedownRequest;
import com.synthdetect.compliance.model.ComplianceAction;
import com.synthdetect.compliance.model.ComplianceReport;
import com.synthdetect.compliance.repository.ComplianceReportRepository;
import com.synthdetect.detection.model.DetectionRequest;
import com.synthdetect.detection.repository.DetectionRequestRepository;
import com.synthdetect.user.model.User;
import com.synthdetect.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private final ComplianceReportRepository reportRepository;
    private final DetectionRequestRepository detectionRequestRepository;
    private final UserService userService;

    @Value("${compliance.takedown-deadline-hours:3}")
    private int takedownDeadlineHours;

    @Transactional
    public ComplianceReport submitTakedown(UUID userId, TakedownRequest req) {
        User user = userService.findActiveUser(userId);

        UUID requestId = UUID.fromString(req.getRequestId());
        DetectionRequest detection = detectionRequestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new ApiException("Detection request not found", HttpStatus.NOT_FOUND));

        if (!Boolean.TRUE.equals(detection.getIsSynthetic())) {
            throw new ApiException("Takedown can only be filed for synthetic content", HttpStatus.BAD_REQUEST);
        }

        Instant deadline = Instant.now().plus(takedownDeadlineHours, ChronoUnit.HOURS);

        ComplianceReport report = ComplianceReport.builder()
                .detectionRequest(detection)
                .user(user)
                .jurisdiction(req.getJurisdiction() != null ? req.getJurisdiction() : "india_it_rules_2026")
                .action(ComplianceAction.TAKEDOWN_REQUESTED)
                .reporterName(req.getReporterName())
                .reporterEmail(req.getReporterEmail())
                .reporterOrg(req.getReporterOrg())
                .description(req.getDescription())
                .deadlineAt(deadline)
                .build();

        // flag the detection for review
        detection.setFlaggedForReview(true);
        detectionRequestRepository.save(detection);

        ComplianceReport saved = reportRepository.save(report);
        log.info("Takedown filed for detection {} by user {} — deadline {}", requestId, userId, deadline);
        return saved;
    }

    @Transactional
    public ComplianceReport resolveReport(UUID userId, UUID reportId, String action, String notes) {
        ComplianceReport report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ApiException("Compliance report not found", HttpStatus.NOT_FOUND));

        ComplianceAction newAction = ComplianceAction.valueOf(action.toUpperCase());
        report.setAction(newAction);
        report.setResolutionNotes(notes);
        report.setResolvedAt(Instant.now());

        return reportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public Page<ComplianceReport> listReports(UUID userId, Pageable pageable) {
        return reportRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public ComplianceReport getReport(UUID userId, UUID reportId) {
        return reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ApiException("Compliance report not found", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getComplianceSummary(UUID userId) {
        long totalFlagged = reportRepository.countByUserIdAndAction(userId, ComplianceAction.FLAGGED);
        long pendingTakedowns = reportRepository.countByUserIdAndAction(userId, ComplianceAction.TAKEDOWN_REQUESTED);
        long resolved = reportRepository.countByUserIdAndAction(userId, ComplianceAction.TAKEDOWN_COMPLETED)
                + reportRepository.countByUserIdAndAction(userId, ComplianceAction.CLEARED);

        return Map.of(
                "totalFlagged", totalFlagged,
                "pendingTakedowns", pendingTakedowns,
                "resolved", resolved,
                "jurisdiction", "india_it_rules_2026",
                "takedownDeadlineHours", takedownDeadlineHours
        );
    }
}
