package com.shinhan.heybob.domain.savings.scheduler;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.savings.entity.SavingsPlan;
import com.shinhan.heybob.domain.savings.repository.SavingsPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@ConditionalOnProperty(value = "savings.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@Component
@EnableScheduling
@RequiredArgsConstructor
public class SavingsPlanScheduler {

    private final SavingsPlanRepository planRepository;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul") // 정각마다
    public void tick() {
        var now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        var dueIds = planRepository.findDueIds(
                SavingsPlan.PlanStatus.ACTIVE,
                now,
                PageRequest.of(0, 100)
        );

        for (Long planId : dueIds) {
            try {
                processPlan(planId);
            } catch (Exception e) {
                log.error("SavingsPlan process failed. planId={}", planId, e);
            }
        }
    }

    @Transactional
    public void processPlan(Long planId) {
        SavingsPlan sp = planRepository.findOneForUpdate(planId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SAVINGS_PLAN_NOT_FOUND)); // 낙관락
        var now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        if (sp.getStatus() != SavingsPlan.PlanStatus.ACTIVE || sp.getNextNotifyAt().isAfter(now)) return;

        var account = sp.getSavingsAccount();
        var meal = account.getMealAppointment();
        Long chatRoomId = meal.getChatRoomId();
        if (chatRoomId == null) {
            log.warn("No chatRoomId for mealId={}", meal.getId());
            // 정책 중 하나: 일단 PAUSED + 내일 같은 시간으로 미룸
            sp.pauseUntil(now.plusDays(1));
            planRepository.save(sp);
            return;
        }

        // 커밋 후 발행: Redis

        // 진행 업데이트
        sp.markSentAndScheduleNext(); // sentCycles++, COMPLETED or next +1week
        planRepository.save(sp);


    }

}


