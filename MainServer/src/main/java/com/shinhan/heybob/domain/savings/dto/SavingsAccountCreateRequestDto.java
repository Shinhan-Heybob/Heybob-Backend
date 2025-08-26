package com.shinhan.heybob.domain.savings.dto;

public record SavingsAccountCreateRequestDto(
        // 인당 1회차 납부 시 금액
        int perHeadBalance,
        // 목표 금액(총 금액)
        int totalAmount
) {}
