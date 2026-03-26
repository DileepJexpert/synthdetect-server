package com.synthdetect.user.repository;

import com.synthdetect.user.model.EmailToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    Optional<EmailToken> findByTokenHashAndUsedAtIsNull(String tokenHash);

    @Modifying
    @Query("DELETE FROM EmailToken t WHERE t.user.id = :userId AND t.type = :type AND t.usedAt IS NULL")
    void deleteUnusedByUserAndType(@Param("userId") UUID userId, @Param("type") EmailToken.Type type);
}
