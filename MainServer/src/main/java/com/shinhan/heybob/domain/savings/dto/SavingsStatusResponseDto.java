package com.shinhan.heybob.domain.savings.dto;

import java.util.List;

public record SavingsStatusResponseDto(
        Long savingsId,
        String status,
        Long initiatorId,
        String initiatorName,
        Long groupId,
        String groupName,
        String groupDescription,
        String meetingDate,
        String meetingTime,
        int targetAmount,
        int currentAmount,
        int perHeadAmount,
        int participantsCount,
        int paidCount,
        int savingsRound,
        List<ParticipantSummaryDto> participants,
        List<SavingsHistoryDto> savingsHistory
) {}
