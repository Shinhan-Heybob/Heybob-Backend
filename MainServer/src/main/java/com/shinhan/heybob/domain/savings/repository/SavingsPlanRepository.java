package com.shinhan.heybob.domain.savings.repository;

import com.shinhan.heybob.domain.savings.entity.SavingsPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SavingsPlanRepository extends JpaRepository<SavingsPlan, Integer> {

    @Query("""
      select sp.id from SavingsPlan sp
      where sp.status = :status and sp.nextNotifyAt <= :now
      order by sp.nextNotifyAt asc
    """)
    List<Long> findDueIds(@Param("status") SavingsPlan.PlanStatus status,
                          @Param("now") LocalDateTime now,
                          Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 동시 중복발행 방지에 더 안전
    @Query("select sp from SavingsPlan sp where sp.id = :id")
    Optional<SavingsPlan> findOneForUpdate(@Param("id") Long id);

    Optional<SavingsPlan> findBySavingsAccount_Id(Long savingsAccountId);
}
