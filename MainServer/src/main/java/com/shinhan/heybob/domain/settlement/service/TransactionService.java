package com.shinhan.heybob.domain.settlement.service;

import com.shinhan.heybob.domain.settlement.dto.SettlementRequestDto;

public interface TransactionService {

    void createSettlement(Long userId, SettlementRequestDto requestDto);
}
