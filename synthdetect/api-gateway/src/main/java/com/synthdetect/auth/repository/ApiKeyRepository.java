package com.synthdetect.auth.repository;

import com.synthdetect.auth.model.ApiKey;
import com.synthdetect.auth.model.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    @Query("SELECT k FROM ApiKey k JOIN FETCH k.user WHERE k.keyHash = :keyHash AND k.status = 'ACTIVE'")
    Optional<ApiKey> findActiveByKeyHash(@Param("keyHash") String keyHash);

    List<ApiKey> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ApiKey> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE ApiKey k SET k.lastUsedAt = :now, k.totalCalls = k.totalCalls + 1 WHERE k.id = :id")
    void updateUsage(@Param("id") UUID id, @Param("now") Instant now);

    long countByUserIdAndStatus(UUID userId, ApiKeyStatus status);
}
