package com.shinhan.heybob.domain.settlement.repository;

import com.shinhan.heybob.domain.settlement.entity.SettlementParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SettlementParticipantRepository extends JpaRepository<SettlementParticipant, Long> {
}
