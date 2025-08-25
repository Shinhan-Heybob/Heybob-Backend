package com.shinhan.heybob.domain.settlement.service;

import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.settlement.dto.SettlementResponseDto;
import com.shinhan.heybob.domain.settlement.entity.Settlement;

import java.util.List;

public interface TransactionService {

    void createSettlement(Long userId, List<Long> participantsUserId, int totalAmount, Long chatRoomId);

    void updateSettlement(Long userId, List<Long> participantsUserIds, int totalAmount, Long chatRoomId);

    void notifySettlement(Long chatRoomId, Long requesterId);

    SettlementResponseDto getSettlementInfo(Long userId, Long chatRoomId);
}
