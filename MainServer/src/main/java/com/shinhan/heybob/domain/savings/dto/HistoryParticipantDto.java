package com.shinhan.heybob.domain.savings.dto;

// 이력 참여자 DTO
public record HistoryParticipantDto(
        Long userId,
        String userName,
        String studentId,
        String department,
        String profileUrl,
        int amount,
        boolean isPaid,
        String paidDate
) {}
