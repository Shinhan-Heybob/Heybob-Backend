package com.shinhan.heybob.domain.settlement.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.settlement.dto.CreateSettlementRequestDto;
import com.shinhan.heybob.domain.settlement.entity.Settlement;
import com.shinhan.heybob.domain.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SettlementFacade {

    private final TransactionService transactionService;
    private final MealAppointmentRepository mealAppointmentRepository;
    private final SettlementRepository settlementRepository;

    public void inputSettlementInfo(Long chatRoomId, Long userId, CreateSettlementRequestDto requestDto) {
        // 1. 채팅으로 밥약 찾기
        MealAppointment mealAppointment = mealAppointmentRepository.findByChatRoomId(chatRoomId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));

        // 2. 밥약에서 정산 있는지 확인
        Optional<Settlement> settlement = settlementRepository.findByMealAppointment(mealAppointment);
        if (settlement.isEmpty()) {
            transactionService.createSettlement(
                    userId,
                    requestDto.participantsUserIds(),
                    requestDto.totalAmount(),
                    mealAppointment
                    );
            return;
        }

        // 3. 정산 있으면 업데이트
        Settlement settle = settlement.get();
        transactionService.updateSettlement(settle, userId, requestDto.participantsUserIds(), requestDto.totalAmount());
    }
}
