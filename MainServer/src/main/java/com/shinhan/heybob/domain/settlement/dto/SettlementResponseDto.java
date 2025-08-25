package com.shinhan.heybob.domain.settlement.dto;

public record SettlementResponseDto(
        Long settlementId,
        Long initiatorId,
        String initiatorName,
        int perHeadAmount,
        int totalAmount,
        int participantsCount,
        boolean isInitiator,
        boolean isParticipant,
        Boolean myPaid
) {}
