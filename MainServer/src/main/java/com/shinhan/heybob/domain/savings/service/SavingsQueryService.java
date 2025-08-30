package com.shinhan.heybob.domain.savings.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.entity.MealParticipant;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.meal.repository.MealParticipantRepository;
import com.shinhan.heybob.domain.savings.dto.HistoryParticipantDto;
import com.shinhan.heybob.domain.savings.dto.ParticipantSummaryDto;
import com.shinhan.heybob.domain.savings.dto.SavingsHistoryDto;
import com.shinhan.heybob.domain.savings.dto.SavingsStatusResponseDto;
import com.shinhan.heybob.domain.savings.entity.SavingsAccount;
import com.shinhan.heybob.domain.savings.entity.SavingsDeposit;
import com.shinhan.heybob.domain.savings.entity.SavingsPlan;
import com.shinhan.heybob.domain.savings.repository.SavingsAccountRepository;
import com.shinhan.heybob.domain.savings.repository.SavingsDepositRepository;
import com.shinhan.heybob.domain.savings.repository.SavingsPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SavingsQueryService {

    private final SavingsAccountRepository savingsAccountRepository;
    private final MealAppointmentRepository mealAppointmentRepository;
    private final MealParticipantRepository mealParticipantRepository;
    private final SavingsDepositRepository savingsDepositRepository;
    private final SavingsPlanRepository savingsPlanRepository;

    /**
     * 정기 모임 페이지(채팅방 기준) 조회
     */
    @Transactional(readOnly = true)
    public SavingsStatusResponseDto getRegularMeetingPageByChatRoom(Long chatRoomId) {
        // 1) 모임, 계좌, 플랜 조회
        MealAppointment mealAppointment = mealAppointmentRepository.findByChatRoomId(chatRoomId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));

        SavingsAccount savingsAccount = savingsAccountRepository.findByMealAppointment_Id(mealAppointment.getId())
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SAVINGS_ACCOUNT_NOT_FOUND));

        SavingsPlan savingsPlan = savingsPlanRepository.findBySavingsAccount_Id(savingsAccount.getId())
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SAVINGS_PLAN_NOT_FOUND));

        // 2) 참여자, 입금 내역 조회
        List<MealParticipant> participants = mealParticipantRepository.findByMealAppointment_Id(mealAppointment.getId());
        List<SavingsDeposit> deposits = savingsDepositRepository.findBySavingsAccount_Id(savingsAccount.getId());

        // 3) 현재 회차 계산: 플랜 sentCycles vs 실제 입금 데이터의 최대 회차 중 큰 값
        int maxCycleFromDeposits = deposits.stream()
                .mapToInt(SavingsDeposit::getCycleNo)
                .max()
                .orElse(0);
        int currentCycle = Math.max(savingsPlan.getSentCycles(), maxCycleFromDeposits);

        // 4) 총 적립 금액(SUCCESS만)
        int totalSavedAmount = deposits.stream()
                .filter(d -> d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                .mapToInt(SavingsDeposit::getAmount)
                .sum();

        // 5) 목표 금액
        int participantsCount = participants.size();
        int perHeadAmount = savingsPlan.getPerHeadBalance();
        int targetAmount = perHeadAmount * participantsCount * savingsPlan.getTotalCycles();

        // 6) 회차별 · 유저별 최신 입금 선택(같은 회차 같은 유저 다중 입금 시 최신 건으로 병합)
        Map<Integer, Map<Long, SavingsDeposit>> depositByCycleByUser = deposits.stream()
                .collect(Collectors.groupingBy(
                        SavingsDeposit::getCycleNo,
                        Collectors.toMap(
                                d -> d.getParticipantUser().getId(),
                                d -> d,
                                (d1, d2) -> {
                                    // SUCCESS 우선 → 최신(createdAt) 우선 병합 정책
                                    boolean s1 = d1.getStatus() == SavingsDeposit.TransferStatus.SUCCESS;
                                    boolean s2 = d2.getStatus() == SavingsDeposit.TransferStatus.SUCCESS;
                                    if (s1 && !s2) return d1;
                                    if (!s1 && s2) return d2;
                                    return d1.getCreatedAt().isAfter(d2.getCreatedAt()) ? d1 : d2;
                                }
                        )
                ));

        // 7) 현재 회차 납입 완료자 수
        int currentCyclePaidCount = 0;
        if (currentCycle > 0) {
            Map<Long, SavingsDeposit> cur = depositByCycleByUser.getOrDefault(currentCycle, Collections.emptyMap());
            currentCyclePaidCount = (int) cur.values().stream()
                    .filter(d -> d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                    .count();
        }

        // 8) 참여자별 총 납입 금액 + 이번 회차 납입 여부
        Map<Long, Integer> totalPaidByUser = deposits.stream()
                .filter(d -> d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                .collect(Collectors.groupingBy(
                        d -> d.getParticipantUser().getId(),
                        Collectors.summingInt(SavingsDeposit::getAmount)
                ));

        Set<Long> currentCyclePaidUsers = (currentCycle > 0)
                ? depositByCycleByUser.getOrDefault(currentCycle, Collections.emptyMap()).values().stream()
                .filter(d -> d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                .map(d -> d.getParticipantUser().getId())
                .collect(Collectors.toSet())
                : Collections.emptySet();

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
                            perHeadAmount,
                            totalPaid,
                            isPaidThisMonth,
                            status
                    );
                })
                .toList();

        // 9) 적금 이력 생성
        // 입금이 존재하는 회차만 이력으로 노출(요구사항에 맞춰 최신 회차 → 과거 회차 정렬)
        List<Integer> cyclesForHistory = depositByCycleByUser.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .toList();

        LocalDate startDate = mealAppointment.getAppointmentDate();

        List<SavingsHistoryDto> savingsHistory = cyclesForHistory.stream()
                .map(cycle -> {
                    Map<Long, SavingsDeposit> byUser = depositByCycleByUser.getOrDefault(cycle, Collections.emptyMap());

                    int cycleTotal = byUser.values().stream()
                            .filter(d -> d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                            .mapToInt(SavingsDeposit::getAmount)
                            .sum();

                    int cyclePaidCount = (int) byUser.values().stream()
                            .filter(d -> d.getStatus() == SavingsDeposit.TransferStatus.SUCCESS)
                            .count();

                    List<HistoryParticipantDto> historyParticipants = participants.stream()
                            .map(p -> {
                                Long uid = p.getUser().getId();
                                SavingsDeposit dep = byUser.get(uid);
                                boolean isPaid = dep != null && dep.getStatus() == SavingsDeposit.TransferStatus.SUCCESS;
                                String paidDate = isPaid ? dep.getCreatedAt().toLocalDate().toString() : null;

                                return new HistoryParticipantDto(
                                        uid,
                                        p.getUser().getName(),
                                        p.getUser().getStudentId(),
                                        p.getUser().getDepartment(),
                                        p.getUser().getProfileUrl(),
                                        perHeadAmount,
                                        isPaid,
                                        paidDate
                                );
                            })
                            .toList();

                    // 회차 날짜: 시작일 기준 (cycle-1)개월 뒤의 말일(예시 스펙과 동일하게 월말 사용)
                    String cycleDateStr = toEndOfMonthDate(startDate, cycle);

                    return new SavingsHistoryDto(
                            cycle,
                            cycleDateStr,
                            cycleTotal,
                            cyclePaidCount,
                            participantsCount,
                            historyParticipants
                    );
                })
                .toList();

        // 10) 최종 응답 DTO
        return new SavingsStatusResponseDto(
                savingsAccount.getId(),
                savingsPlan.getStatus().name(),
                savingsAccount.getOwnerUser().getId(),
                savingsAccount.getOwnerUser().getName(),
                mealAppointment.getId(),
                mealAppointment.getName(),
                mealAppointment.getMemo(), // 필요시 DB/설정값으로 교체
                mealAppointment.getAppointmentDate().toString(),
                mealAppointment.getAppointmentTime().toString(),
                targetAmount,
                totalSavedAmount,
                perHeadAmount,
                participantsCount,
                currentCyclePaidCount,
                currentCycle,
                participantSummaries,
                savingsHistory
        );
    }

    /**
     * 모임 시작일 기준 (round-1)개월 뒤의 "말일"을 yyyy-MM-dd로 반환
     * 예: 시작일 2025-04-10, round=5 → 2025-08-31
     */
    private static String toEndOfMonthDate(LocalDate startDate, int round) {
        LocalDate base = startDate.plusMonths(Math.max(1, round) - 1L);
        YearMonth ym = YearMonth.from(base);
        return ym.atEndOfMonth().toString();
    }
}
