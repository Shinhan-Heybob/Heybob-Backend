package com.shinhan.heybob.domain.settlement.service;

import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.settlement.entity.Settlement;

import java.util.List;

public interface TransactionService {

    void createSettlement(Long userId, List<Long> participantsUserId, int totalAmount, MealAppointment mealAppointment);

    void updateSettlement(Settlement settlement, Long userId, List<Long> participantsUserIds, int totalAmount);
}
