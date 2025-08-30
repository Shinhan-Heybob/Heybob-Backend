package com.shinhan.heybob.domain.savings.dto;

import java.util.List;

// 적금 이력 DTO
public record SavingsHistoryDto(
        int round,
        String date,
        int totalAmount,
        int paidCount,
        int totalCount,
        List<HistoryParticipantDto> participants
) {}
