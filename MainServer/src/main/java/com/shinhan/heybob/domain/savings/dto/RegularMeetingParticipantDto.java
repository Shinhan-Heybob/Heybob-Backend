package com.shinhan.heybob.domain.savings.dto;

import java.util.List;

public record RegularMeetingParticipantDto(
        Long userId,
        String userName,
        int totalSavedAmount,
        int paidCycles,
        List<CycleDepositDto> cycleDeposits
) {}