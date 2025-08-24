package com.shinhan.heybob.chat.domain.chat.scheduler;

import com.shinhan.heybob.chat.global.util.FallbackMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FallbackMetricsScheduler {
    
    private final FallbackMetrics fallbackMetrics;
    
    @Scheduled(fixedRate = 300000)  // 5분마다 통계 로깅
    public void logFallbackStats() {
        try {
            fallbackMetrics.logCurrentStats();
            
            double fallbackRate = fallbackMetrics.getFallbackRate();
            if (fallbackRate > 10.0) {  // Fallback 비율이 10% 초과시 경고
                log.warn("🚨 MongoDB Fallback 비율이 높습니다: {:.2f}% - Redis 상태를 확인하세요!", fallbackRate);
            }
            
            if (fallbackMetrics.getTotalFailureCount() > 0) {
                log.error("💀 금융 메시지 유실이 발생했습니다: {} 건 - 즉시 조치 필요!", 
                    fallbackMetrics.getTotalFailureCount());
            }
            
        } catch (Exception e) {
            log.error("Fallback 통계 로깅 중 오류", e);
        }
    }
}