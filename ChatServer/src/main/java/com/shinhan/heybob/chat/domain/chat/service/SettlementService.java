package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.PaymentRequestData;
import java.util.List;

public interface SettlementService {
    
    PaymentRequestData createSettlement(String roomId, String requesterId, String note, Integer totalAmount);
    
    PaymentRequestData updateSettlementResponse(String settlementId, String userId, String responseType);
    
    PaymentRequestData getSettlement(String settlementId);
    
    List<String> getRoomMembers(String roomId);
    
    void processSettlementCompletion(String settlementId);
    
    void handleSettlementTimeout(String settlementId);
}