package com.shinhan.heybob.domain.settlement.service;

import com.shinhan.heybob.domain.settlement.dto.CreateSettlementRequestDto;

public interface TransactionService {

    void createSettlement(Long userId, CreateSettlementRequestDto requestDto);
}
