package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import java.util.List;

public interface SettlementService {
    
    SettlementData createSettlement(String roomId, String requesterId, String note, Integer totalAmount);
    
    SettlementData updateSettlementResponse(String settlementId, String userId, String responseType);
    
    SettlementData getSettlement(String settlementId);
    
    List<String> getRoomMembers(String roomId);
    
    void processSettlementCompletion(String settlementId);
    
    void handleSettlementTimeout(String settlementId);
}