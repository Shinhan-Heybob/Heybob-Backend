package com.shinhan.heybob.domain.savings.dto;

import com.shinhan.heybob.domain.savings.entity.SavingsDeposit;

public record CycleDepositDto(
        int cycleNo,
        int amount,
        boolean paid,
        SavingsDeposit.TransferStatus status
) {}