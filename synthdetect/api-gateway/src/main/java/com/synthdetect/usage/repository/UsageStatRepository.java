package com.synthdetect.usage.repository;

import com.synthdetect.usage.model.UsageStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsageStatRepository extends JpaRepository<UsageStat, UUID> {

    Optional<UsageStat> findByUserIdAndYearMonth(UUID userId, String yearMonth);

    List<UsageStat> findByUserIdOrderByYearMonthDesc(UUID userId);

    @Query("SELECT u FROM UsageStat u WHERE u.user.id = :userId ORDER BY u.yearMonth DESC")
    List<UsageStat> findLastNMonths(@Param("userId") UUID userId);
}
