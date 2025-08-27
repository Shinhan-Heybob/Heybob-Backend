package com.shinhan.heybob.domain.savings.repository;

import com.shinhan.heybob.domain.savings.entity.SavingsDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface SavingsDepositRepository extends JpaRepository<SavingsDeposit, Long> {

    // 회차별 성공한 사용자 ID 집합
    @Query("""
        select d.participantUser.id
          from SavingsDeposit d
         where d.savingsAccount.id = :accountId
           and d.cycleNo = :cycleNo
           and d.status = 'SUCCESS'
    """)
    Set<Long> findPaidUserIds(Long accountId, int cycleNo);

    boolean existsBySavingsAccount_IdAndParticipantUser_IdAndCycleNoAndStatus(
            Long accountId, Long userId, int cycleNo, SavingsDeposit.TransferStatus status);

    Optional<SavingsDeposit> findBySavingsAccount_IdAndParticipantUser_IdAndCycleNo(
            Long accountId, Long userId, int cycleNo);
}
