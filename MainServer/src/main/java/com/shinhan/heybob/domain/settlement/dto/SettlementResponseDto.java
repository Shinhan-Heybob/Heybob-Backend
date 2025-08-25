package com.shinhan.heybob.domain.settlement.dto;


public record SettlementResponseDto(
        int totalAmount,
        int participantsCount,
        int perHeadAmount
) {}
