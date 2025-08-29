package com.shinhan.heybob.domain.savings.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.entity.MealParticipant;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.meal.repository.MealParticipantRepository;
import com.shinhan.heybob.domain.savings.dto.CycleParticipantDto;
import com.shinhan.heybob.domain.savings.dto.CycleStatusDto;
import com.shinhan.heybob.domain.savings.dto.RegularMeetingPageResponseDto;
import com.shinhan.heybob.domain.savings.dto.SavingsParticipantStatusDto;
import com.shinhan.heybob.domain.savings.entity.SavingsAccount;
import com.shinhan.heybob.domain.savings.entity.SavingsDeposit;
import com.shinhan.heybob.domain.savings.entity.SavingsPlan;
import com.shinhan.heybob.domain.savings.repository.SavingsAccountRepository;
import com.shinhan.heybob.domain.savings.repository.SavingsDepositRepository;
import com.shinhan.heybob.domain.savings.repository.SavingsPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SavingsQueryService {

    private final SavingsAccountRepository savingsAccountRepository;
    private final MealAppointmentRepository mealAppointmentRepository;
    private final MealParticipantRepository mealParticipantRepository;
    private final SavingsDepositRepository savingsDepositRepository;
    private final SavingsPlanRepository savingsPlanRepository;

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

    @Transactional(readOnly = true)
    public RegularMeetingPageResponseDto getRegularMeetingPageByChatRoom(Long chatRoomId) {
        // 정기 모임 정보 가져오기
        MealAppointment mealAppointment = mealAppointmentRepository.findByChatRoomId(chatRoomId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));

        // 적금 계좌 정보 가져오기
        SavingsAccount savingsAccount = savingsAccountRepository.findByMealAppointment_Id(mealAppointment.getId())
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SAVINGS_ACCOUNT_NOT_FOUND));

        // 적금 플랜 정보 가져오기
        SavingsPlan savingsPlan = savingsPlanRepository.findBySavingsAccount_Id(savingsAccount.getId())
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SAVINGS_PLAN_NOT_FOUND));

        // 참여자 목록 가져오기
        List<MealParticipant> participants = mealParticipantRepository.findByMealAppointment_Id(mealAppointment.getId());

        // 모든 저금 내역 가져오기
        List<SavingsDeposit> deposits = savingsDepositRepository.findBySavingsAccount_Id(savingsAccount.getId());

        // 회차별 저금 내역 그룹핑
        Map<Integer, List<SavingsDeposit>> depositsByCycle = deposits.stream()
                .collect(Collectors.groupingBy(SavingsDeposit::getCycleNo));

        // 총 적립 금액 계산
        int totalSavedAmount = deposits.stream()
                .filter(d -> d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                .mapToInt(SavingsDeposit::getAmount)
                .sum();

        // 회차별 상태 매핑 (1부터 현재 회차까지)
        List<CycleStatusDto> cycleDtos = List.of();
        if (savingsPlan.getSentCycles() > 0) {
            cycleDtos = java.util.stream.IntStream.rangeClosed(1, savingsPlan.getSentCycles())
                    .mapToObj(cycleNo -> {
                        List<SavingsDeposit> cycleDeposits = depositsByCycle.getOrDefault(cycleNo, List.of());
                        Map<Long, SavingsDeposit> depositsByUserId = cycleDeposits.stream()
                                .collect(Collectors.toMap(d -> d.getParticipantUser().getId(), d -> d));

                        // 회차별 참여자 상태 매핑
                        List<CycleParticipantDto> cycleParticipants = participants.stream()
                                .map(p -> {
                                    SavingsDeposit deposit = depositsByUserId.get(p.getUser().getId());
                                    if (deposit != null) {
                                        return new CycleParticipantDto(
                                                p.getUser().getId(),
                                                p.getUser().getName(),
                                                p.getUser().getStudentId(),
                                                p.getUser().getDepartment(),
                                                p.getUser().getProfileUrl(),
                                                deposit.getAmount(),
                                                deposit.getStatus() == SavingsDeposit.TransferStatus.SUCCESS,
                                                deposit.getStatus()
                                        );
                                    } else {
                                        return new CycleParticipantDto(
                                                p.getUser().getId(),
                                                p.getUser().getName(),
                                                p.getUser().getStudentId(),
                                                p.getUser().getDepartment(),
                                                p.getUser().getProfileUrl(),
                                                savingsPlan.getPerHeadBalance(),
                                                false,
                                                SavingsDeposit.TransferStatus.PENDING
                                        );
                                    }
                                })
                                .toList();

                        int expectedAmount = participants.size() * savingsPlan.getPerHeadBalance();
                        int actualAmount = cycleDeposits.stream()
                                .filter(d -> d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                                .mapToInt(SavingsDeposit::getAmount)
                                .sum();
                        int paidCount = (int) cycleParticipants.stream().filter(CycleParticipantDto::paid).count();

                        return new CycleStatusDto(
                                cycleNo,
                                expectedAmount,
                                actualAmount,
                                participants.size(),
                                paidCount,
                                cycleParticipants
                        );
                    })
                    .toList();
        }

        return new RegularMeetingPageResponseDto(
                mealAppointment.getId(),
                mealAppointment.getName(),
                mealAppointment.getAppointmentDate(),
                mealAppointment.getAppointmentTime(),
                mealAppointment.getCreator().getId(),
                mealAppointment.getCreator().getName(),
                savingsAccount.getId(),
                savingsAccount.getAccountNo(),
                savingsPlan.getId(),
                savingsPlan.getPerHeadBalance(),
                savingsPlan.getSentCycles(),
                savingsPlan.getTotalCycles(),
                savingsPlan.getStatus(),
                totalSavedAmount,
                participants.size(),
                cycleDtos
        );
    }
}
