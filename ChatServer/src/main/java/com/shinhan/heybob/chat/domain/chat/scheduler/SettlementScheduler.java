package com.shinhan.heybob.chat.domain.chat.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementScheduler {
    
    // 단순화된 정산 스케줄러 - 실제 정산 만료 처리는 Main 서버에서 담당
    
    @Scheduled(fixedRate = 300000)  // 5분마다 실행 (로그 출력용)
    public void healthCheck() {
        log.debug("정산 스케줄러 상태 확인 (단순화 버전)");
    }
}