package com.shinhan.heybob.domain.savings.scheduler;

import com.shinhan.heybob.domain.savings.entity.SavingsPlan;
import com.shinhan.heybob.domain.savings.repository.SavingsPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class SavingsPlanScheduler {

    private final SavingsPlanRepository planRepository;

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

        // 커밋 후 발행: Redis

        // 진행 업데이트
        sp.markSentAndScheduleNext(); // sentCycles++, COMPLETED or next +1week
        planRepository.save(sp);
    }
}
