package com.shinhan.heybob.domain.settlement.service;

import com.shinhan.heybob.domain.settlement.dto.SettlementResponseDto;

import java.util.List;

public interface SettlementService {

    void createSettlement(Long userId, List<Long> participantsUserId, int totalAmount, Long chatRoomId);

    void updateSettlement(Long userId, List<Long> participantsUserIds, int totalAmount, Long chatRoomId);

    void notifySettlement(Long chatRoomId, Long requesterId);

    SettlementResponseDto getSettlementInfo(Long userId, Long chatRoomId);

    void paySettlement(Long userId, Long requesterId);
}
