package com.shinhan.heybob.domain.savings.scheduler;

import com.shinhan.heybob.domain.notification.ChatEventMessageDto;
import com.shinhan.heybob.domain.notification.NotificationEventType;
import com.shinhan.heybob.domain.notification.publisher.RedisStreamPublisher;
import com.shinhan.heybob.domain.savings.entity.SavingsPlan;
import com.shinhan.heybob.domain.savings.repository.SavingsPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class SavingsPlanScheduler {

    private final SavingsPlanRepository planRepository;
    private final RedisStreamPublisher redisStreamPublisher;

    @Scheduled(fixedDelayString = "PT1M")
    public void tick() {
        var now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        List<SavingsPlan> due = planRepository.findDue(SavingsPlan.PlanStatus.ACTIVE, now);
        for (SavingsPlan plan : due) {
            try {
                processPlan(plan.getId());
            } catch (Exception e) {
                log.error("SavingsPlan process failed. planId={}", plan.getId(), e);
            }
        }
    }

    @Transactional
    public void processPlan(Long planId) {
        SavingsPlan sp = planRepository.findOneForUpdate(planId); // 낙관락
        var now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        if (sp.getStatus() != SavingsPlan.PlanStatus.ACTIVE || sp.getNextNotifyAt().isAfter(now)) return;

        var account = sp.getSavingsAccount();
        var meal = account.getMealAppointment();
        Long chatRoomId = meal.getChatRoomId();
        if (chatRoomId == null) {
            // 채팅방이 없으면 스킵 or 로그
            log.warn("No chatRoomId for mealId={}", meal.getId());
            // 다음 주로 넘기지 말고 관리자 알림 등 정책에 따라 처리
            return;
        }

        // 메시지 구성
        String amountLabel = NumberFormat.getInstance(Locale.KOREA).format(sp.getPerHeadBalance()) + "원";
        int currentCycle = sp.getSentCycles() + 1;
        String title = String.format("[적금 알림] 이번 주 %s 이체해주세요 (%d/%d)", amountLabel, currentCycle, sp.getTotalCycles());
        String ctaLabel = amountLabel + " 이체하기";

        var event = new ChatEventMessageDto(
                NotificationEventType.SAVINGS_REMINDER, // 새 타입 정의 추천
                chatRoomId,
                null, // settlementId 없음
                account.getOwnerUser().getId(),
                account.getOwnerUser().getName(),
                title,
                ctaLabel
        );

        // 커밋 후 발행
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                redisStreamPublisher.publish(event);
                log.info("Savings reminder published: planId={}, roomId={}, cycle={}/{}",
                        sp.getId(), chatRoomId, currentCycle, sp.getTotalCycles());
            }
        });

        // 진행 업데이트
        sp.markSentAndScheduleNext(); // sentCycles++, COMPLETED or next +1week
        planRepository.save(sp);
    }
}
