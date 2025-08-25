package com.shinhan.heybob.domain.settlement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;


public record SettlementRequestDto(
        @Min(1) int totalAmount,
        @NotEmpty List<Long> participantsUserIds
) {}
