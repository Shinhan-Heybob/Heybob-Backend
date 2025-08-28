package com.shinhan.heybob.domain.settlement.repository;

import com.shinhan.heybob.domain.settlement.entity.SettlementParticipant;
import com.shinhan.heybob.domain.settlement.model.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettlementParticipantRepository extends JpaRepository<SettlementParticipant, Long> {
    Optional<SettlementParticipant> findBySettlement_IdAndParticipantUser_Id(Long settlementId, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SettlementParticipant sp where sp.settlement.id = :settlementId")
    void deleteBySettlementId(@Param("settlementId") Long settlementId);

    @Query("select count(sp) from SettlementParticipant sp " +
            "where sp.settlement.id = :settlementId and sp.transferStatus = :status")
    long countBySettlementAndStatus(@Param("settlementId") Long settlementId,
                                    @Param("status") TransferStatus status);
}
