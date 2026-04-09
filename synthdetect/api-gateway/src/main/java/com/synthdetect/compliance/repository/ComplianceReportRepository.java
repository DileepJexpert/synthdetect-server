package com.synthdetect.compliance.repository;

import com.synthdetect.compliance.model.ComplianceAction;
import com.synthdetect.compliance.model.ComplianceReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceReportRepository extends JpaRepository<ComplianceReport, UUID> {

    Page<ComplianceReport> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<ComplianceReport> findByIdAndUserId(UUID id, UUID userId);

    List<ComplianceReport> findByActionInAndDeadlineAtBefore(
            List<ComplianceAction> actions, Instant now);

    @Query("SELECT c FROM ComplianceReport c WHERE c.action IN ('FLAGGED','TAKEDOWN_REQUESTED') ORDER BY c.deadlineAt ASC")
    Page<ComplianceReport> findPending(Pageable pageable);

    long countByUserIdAndAction(UUID userId, ComplianceAction action);
}
