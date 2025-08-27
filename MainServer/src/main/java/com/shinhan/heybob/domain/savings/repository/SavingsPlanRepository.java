package com.shinhan.heybob.domain.savings.repository;

import com.shinhan.heybob.domain.savings.entity.SavingsPlan;
import jakarta.persistence.LockModeType;
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
        select sp
          from SavingsPlan sp
         where sp.status = :status
           and sp.nextNotifyAt <= :now
        """)
    List<SavingsPlan> findDue(@Param("status") SavingsPlan.PlanStatus status,
                              @Param("now") LocalDateTime now);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("select sp from SavingsPlan sp where sp.id = :id")
    default SavingsPlan findOneForUpdate(@Param("id") Long id) {
        return null;
    }

    Optional<SavingsPlan> findBySavingsAccount_Id(Long savingsAccountId);
}
