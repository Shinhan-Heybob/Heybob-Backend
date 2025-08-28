package com.shinhan.heybob.domain.settlement.dto;

public record SettlementStartBroadcastRequestDto(
        Long settlementId,
        Integer perHead
) {
}
