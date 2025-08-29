package com.shinhan.heybob.domain.savings.dto;

import com.shinhan.heybob.domain.savings.entity.SavingsDeposit;

public record CycleParticipantDto(
        Long userId,
        String userName,
        String studentId,
        String department,
        String profileUrl,
        int amount,
        boolean paid,
        SavingsDeposit.TransferStatus status
) {}