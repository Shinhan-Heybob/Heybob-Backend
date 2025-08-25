package com.shinhan.heybob.domain.settlement.repository;

import com.shinhan.heybob.domain.settlement.entity.SettlementParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettlementParticipantRepository extends JpaRepository<SettlementParticipant, Long> {
    Optional<SettlementParticipant> findBySettlement_IdAndParticipantUser_Id(Long settlementId, Long userId);
}
