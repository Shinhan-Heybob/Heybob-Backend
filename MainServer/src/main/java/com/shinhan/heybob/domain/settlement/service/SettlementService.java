package com.shinhan.heybob.domain.settlement.service;

import com.shinhan.heybob.domain.settlement.dto.SettlementCreateResponseDto;
import com.shinhan.heybob.domain.settlement.dto.SettlementResponseDto;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

public interface SettlementService {

    ResponseEntity<Map<String, Object>> createSettlement(Long userId, List<Long> participantsUserId, int totalAmount, Long chatRoomId);

    void updateSettlement(Long userId, List<Long> participantsUserIds, int totalAmount, Long chatRoomId);

    SettlementResponseDto getSettlementInfo(Long userId, Long chatRoomId);

    void paySettlement(Long userId, Long requesterId);
}
