package com.synthdetect.scheduler;

import com.synthdetect.compliance.model.ComplianceAction;
import com.synthdetect.compliance.model.ComplianceReport;
import com.synthdetect.compliance.repository.ComplianceReportRepository;
import com.synthdetect.user.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Checks compliance reports approaching or past their takedown deadline.
 * Runs every 15 minutes.
 * - Warns at 30 minutes before deadline
 * - Escalates reports that are past deadline and still open
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceDeadlineJob {

    private final ComplianceReportRepository reportRepository;
    private final EmailService emailService;

    @Scheduled(fixedDelay = 15 * 60 * 1000)  // every 15 minutes
    @Transactional
    public void checkDeadlines() {
        Instant now = Instant.now();
        Instant warningThreshold = now.plusSeconds(30 * 60); // 30 min ahead

        List<ComplianceReport> openReports = reportRepository.findByActionInAndDeadlineAtBefore(
                List.of(ComplianceAction.FLAGGED, ComplianceAction.TAKEDOWN_REQUESTED),
                warningThreshold
        );

        int escalated = 0;
        int warned = 0;

        for (ComplianceReport report : openReports) {
            if (report.getDeadlineAt() == null) continue;

            if (report.getDeadlineAt().isBefore(now)) {
                // Past deadline — escalate
                report.setAction(ComplianceAction.ESCALATED);
                reportRepository.save(report);
                escalated++;

                String email = report.getUser().getEmail();
                emailService.sendComplianceAlert(email,
                        "ESCALATED: Takedown deadline passed for report " + report.getId(),
                        "The takedown deadline for detection request " + report.getDetectionRequest().getId()
                        + " has passed without resolution. Report has been escalated.");

            } else {
                // Approaching deadline — warn
                warned++;
                String email = report.getUser().getEmail();
                long minutesLeft = (report.getDeadlineAt().getEpochSecond() - now.getEpochSecond()) / 60;
                emailService.sendComplianceAlert(email,
                        "REMINDER: Takedown deadline in " + minutesLeft + " minutes",
                        "Compliance report " + report.getId() + " for detection "
                        + report.getDetectionRequest().getId()
                        + " must be resolved within " + minutesLeft + " minutes.");
            }
        }

        if (escalated > 0 || warned > 0) {
            log.info("ComplianceDeadlineJob: escalated={} warned={}", escalated, warned);
        }
    }
}
