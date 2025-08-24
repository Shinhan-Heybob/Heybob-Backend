package com.shinhan.heybob.chat.domain.communication.scheduler;

import com.shinhan.heybob.chat.domain.communication.service.MainServerCommunicationService;
import com.shinhan.heybob.chat.domain.communication.util.RetryMechanism;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommunicationMaintenanceScheduler {
    
    private final MainServerCommunicationService mainServerCommunicationService;
    private final RetryMechanism retryMechanism;
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * Main 서버와의 연결 상태 확인 (1분마다)
     */
    @Scheduled(fixedRate = 60000)
    public void checkMainServerHealth() {
        try {
            log.debug("💓 Main 서버 상태 확인 시작");
            
            // Heartbeat 전송
            mainServerCommunicationService.sendHeartbeat();
            
            // 연결 상태 로깅
            boolean healthy = mainServerCommunicationService.isMainServerHealthy();
            if (!healthy) {
                log.warn("⚠️ Main 서버 연결 상태 불량 감지");
            }
            
        } catch (Exception e) {
            log.error("❌ Main 서버 상태 확인 중 오류", e);
        }
    }
    
    /**
     * 만료된 메시지 정리 (5분마다)
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredMessages() {
        try {
            log.debug("🧹 만료된 통신 메시지 정리 시작");
            retryMechanism.cleanupExpiredMessages(redisTemplate);
        } catch (Exception e) {
            log.error("❌ 만료 메시지 정리 중 오류", e);
        }
    }
    
    /**
     * 통신 통계 리포트 (30분마다)
     */
    @Scheduled(fixedRate = 1800000)
    public void generateCommunicationReport() {
        try {
            log.info("📊 서버간 통신 통계 리포트 생성");
            
            // TODO: 통신 통계 수집 및 리포트 생성
            // - 성공한 메시지 수
            // - 실패한 메시지 수
            // - 재시도 현황
            // - 평균 응답 시간
            // - Circuit Breaker 상태
            
            log.info("📈 통신 통계: [구현 예정]");
            
        } catch (Exception e) {
            log.error("❌ 통신 통계 생성 중 오류", e);
        }
    }
}