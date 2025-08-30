package com.shinhan.heybob.domain.savings.dto;

// 참여자 요약 DTO
public record ParticipantSummaryDto(
        Long userId,
        String userName,
        String studentId,
        String department,
        String profileUrl,
        int monthlyAmount,
        int totalPaidAmount,
        boolean isPaidThisMonth,
        String status
) {}
