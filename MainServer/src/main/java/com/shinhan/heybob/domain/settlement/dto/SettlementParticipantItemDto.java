package com.shinhan.heybob.domain.settlement.dto;

public record SettlementParticipantItemDto(
        Long userId,
        String userName,   // 필요시 nickname 등으로 교체
        String studentId,  // 학번
        String department, // 학과
        String profileUrl, // 프로필 이미지
        int amount,        // 스냅샷 금액
        boolean paid,      // TransferStatus == SUCCESS
        String status      // "PENDING" | "SUCCESS" | "FAILED" | "CANCELED"
) {
}
