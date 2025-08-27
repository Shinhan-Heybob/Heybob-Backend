package com.shinhan.heybob.domain.settlement.dto;

public record SettlementParticipantItemDto(
        Long userId,
        String userName,   // 필요시 nickname 등으로 교체
        int amount,        // 스냅샷 금액
        boolean paid,      // TransferStatus == SUCCESS
        String status      // "PENDING" | "SUCCESS" | "FAILED" | "CANCELED"
) {
}
