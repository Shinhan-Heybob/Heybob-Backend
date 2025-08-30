package com.shinhan.heybob.domain.savings.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.entity.MealParticipant;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.meal.repository.MealParticipantRepository;
import com.shinhan.heybob.domain.savings.dto.*;
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
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class SavingsQueryService {

    private final SavingsAccountRepository savingsAccountRepository;
    private final MealAppointmentRepository mealAppointmentRepository;
    private final MealParticipantRepository mealParticipantRepository;
    private final SavingsDepositRepository savingsDepositRepository;
    private final SavingsPlanRepository savingsPlanRepository;

    @Transactional(readOnly = true)
    public SavingsStatusResponseDto getRegularMeetingPageByChatRoom(Long chatRoomId) {
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

        // 현재 회차
        int currentCycle = savingsPlan.getSentCycles();

        // 총 적립 금액
        int totalSavedAmount = deposits.stream()
                .filter(d -> d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                .mapToInt(SavingsDeposit::getAmount)
                .sum();

        // 목표 금액 계산
        int targetAmount = savingsPlan.getPerHeadBalance() * participants.size() * savingsPlan.getTotalCycles();

        // 현재 회차 납입 완료자 수
        int currentCyclePaidCount = (int) deposits.stream()
                .filter(d -> d.getCycleNo() == currentCycle &&
                        d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                .count();

        // 참여자별 총 납입 금액과 현재 회차 납입 상태
        Map<Long, Integer> totalPaidByUser = deposits.stream()
                .filter(d -> d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                .collect(Collectors.groupingBy(
                        d -> d.getParticipantUser().getId(),
                        Collectors.summingInt(SavingsDeposit::getAmount)
                ));

        Set<Long> currentCyclePaidUsers = deposits.stream()
                .filter(d -> d.getCycleNo() == currentCycle &&
                        d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                .map(d -> d.getParticipantUser().getId())
                .collect(Collectors.toSet());

        // 참여자 요약 DTO 생성
        List<ParticipantSummaryDto> participantSummaries = participants.stream()
                .map(p -> {
                    Long userId = p.getUser().getId();
                    int totalPaid = totalPaidByUser.getOrDefault(userId, 0);
                    boolean isPaidThisMonth = currentCyclePaidUsers.contains(userId);
                    String status = isPaidThisMonth ? "PAID" : "PENDING";

                    return new ParticipantSummaryDto(
                            userId,
                            p.getUser().getName(),
                            p.getUser().getStudentId(),
                            p.getUser().getDepartment(),
                            p.getUser().getProfileUrl(),
                            savingsPlan.getPerHeadBalance(),
                            totalPaid,
                            isPaidThisMonth,
                            status
                    );
                })
                .toList();

        // 적금 이력 생성 (회차별)
        Map<Integer, List<SavingsDeposit>> depositsByCycle = deposits.stream()
                .collect(Collectors.groupingBy(SavingsDeposit::getCycleNo));

        List<SavingsHistoryDto> savingsHistory = IntStream.rangeClosed(1, currentCycle)
                .mapToObj(cycle -> {
                    List<SavingsDeposit> cycleDeposits = depositsByCycle.getOrDefault(cycle, List.of());
                    Map<Long, SavingsDeposit> depositsByUserId = cycleDeposits.stream()
                            .collect(Collectors.toMap(d -> d.getParticipantUser().getId(), d -> d));

                    int cycleTotal = cycleDeposits.stream()
                            .filter(d -> d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                            .mapToInt(SavingsDeposit::getAmount)
                            .sum();

                    int cyclePaidCount = (int) cycleDeposits.stream()
                            .filter(d -> d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                            .count();

                    List<HistoryParticipantDto> historyParticipants = participants.stream()
                            .map(p -> {
                                SavingsDeposit deposit = depositsByUserId.get(p.getUser().getId());
                                boolean isPaid = deposit != null &&
                                        deposit.getStatus() == SavingsDeposit.TransferStatus.SUCCESS;
                                String paidDate = isPaid ? deposit.getCreatedAt().toLocalDate().toString() : null;

                                return new HistoryParticipantDto(
                                        p.getUser().getId(),
                                        p.getUser().getName(),
                                        p.getUser().getStudentId(),
                                        p.getUser().getDepartment(),
                                        p.getUser().getProfileUrl(),
                                        savingsPlan.getPerHeadBalance(),
                                        isPaid,
                                        paidDate
                                );
                            })
                            .toList();

                    return new SavingsHistoryDto(
                            cycle,
                            "2025-" + String.format("%02d", cycle) + "-31", // 실제 날짜 로직 필요
                            cycleTotal,
                            cyclePaidCount,
                            participants.size(),
                            historyParticipants
                    );
                })
                .sorted((a, b) -> Integer.compare(b.round(), a.round())) // 최신순
                .toList();

        return new SavingsStatusResponseDto(
                savingsAccount.getId(),
                savingsPlan.getStatus().name(),
                savingsAccount.getOwnerUser().getId(),
                savingsAccount.getOwnerUser().getName(),
                mealAppointment.getId(),
                mealAppointment.getName(),
                "우리 모두 함께 모아요!", // 기본 설명 또는 DB에서 가져오기
                mealAppointment.getAppointmentDate().toString(),
                mealAppointment.getAppointmentTime().toString(),
                targetAmount,
                totalSavedAmount,
                savingsPlan.getPerHeadBalance(),
                participants.size(),
                currentCyclePaidCount,
                currentCycle,
                participantSummaries,
                savingsHistory
        );
    }
}
