package com.shinhan.heybob.domain.savings.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.entity.MealParticipant;
import com.shinhan.heybob.domain.meal.repository.MealParticipantRepository;
import com.shinhan.heybob.domain.savings.dto.SavingsParticipantStatusDto;
import com.shinhan.heybob.domain.savings.entity.SavingsAccount;
import com.shinhan.heybob.domain.savings.repository.SavingsAccountRepository;
import com.shinhan.heybob.domain.savings.repository.SavingsDepositRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SavingsQueryService {

    private final SavingsAccountRepository savingsAccountRepository;
    private final MealParticipantRepository mealParticipantRepository;
    private final SavingsDepositRepository savingsDepositRepository;

    @Transactional(readOnly = true)
    public List<SavingsParticipantStatusDto> getCycleStatus(Long mealId, int cycleNo) {
        // 1) 적금계좌 찾기 (mealId 기준)
        SavingsAccount acc = savingsAccountRepository.findByMealAppointment_Id(mealId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SAVINGS_ACCOUNT_NOT_FOUND));

        // 2) 모임 참가자 목록 (스냅샷 고정이 필요하면 SavingsMember 사용)
        List<MealParticipant> participants = mealParticipantRepository.findByMealAppointment_Id(mealId);

        // 3) 성공한 사용자 집합
        Set<Long> paidIds = savingsDepositRepository.findPaidUserIds(acc.getId(), cycleNo);

        // 4) DTO 매핑
        return participants.stream()
                .map(mp -> new SavingsParticipantStatusDto(
                        mp.getUser().getId(),
                        mp.getUser().getName(),
                        paidIds.contains(mp.getUser().getId())
                ))
                .toList();
    }
}
