package com.synthdetect.detection.repository;

import com.synthdetect.detection.model.DetectionRequest;
import com.synthdetect.detection.model.DetectionStatus;
import com.synthdetect.detection.model.DetectionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DetectionRequestRepository extends JpaRepository<DetectionRequest, UUID> {

    Page<DetectionRequest> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<DetectionRequest> findByIdAndUserId(UUID id, UUID userId);

    List<DetectionRequest> findByStatus(DetectionStatus status);

    @Query("SELECT d FROM DetectionRequest d WHERE d.user.id = :userId AND d.createdAt >= :since")
    List<DetectionRequest> findByUserIdSince(@Param("userId") UUID userId, @Param("since") Instant since);

    @Query("SELECT COUNT(d) FROM DetectionRequest d WHERE d.user.id = :userId AND d.status = 'COMPLETED' AND FUNCTION('TO_CHAR', d.createdAt, 'YYYY-MM') = :yearMonth")
    long countCompletedByUserAndMonth(@Param("userId") UUID userId, @Param("yearMonth") String yearMonth);

    @Query("SELECT d FROM DetectionRequest d WHERE d.flaggedForReview = true ORDER BY d.createdAt DESC")
    Page<DetectionRequest> findFlagged(Pageable pageable);

    Page<DetectionRequest> findByUserIdAndType(UUID userId, DetectionType type, Pageable pageable);
}
