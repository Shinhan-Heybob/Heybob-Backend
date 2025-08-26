package com.shinhan.heybob.chat.domain.communication.util;

import com.shinhan.heybob.chat.domain.communication.dto.ServerMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RetryMechanism {
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static final String RETRY_QUEUE = "communication-retry-queue";
    private static final int MAX_RETRY_COUNT = 3;
    private static final int[] RETRY_DELAYS = {5, 30, 180}; // 5초, 30초, 3분
    
    /**
     * 실패한 메시지를 재시도 큐에 추가
     */
    public void scheduleRetry(ServerMessage failedMessage, Exception error, 
                             Runnable retryAction) {
        try {
            int currentRetry = failedMessage.getRetryCount();
            
            if (currentRetry >= MAX_RETRY_COUNT) {
                log.error("💀 최대 재시도 횟수 초과, 메시지 포기: messageId={}, messageType={}", 
                    failedMessage.getMessageId(), failedMessage.getMessageType());
                handleMaxRetryExceeded(failedMessage, error);
                return;
            }
            
            int delaySeconds = RETRY_DELAYS[currentRetry];
            failedMessage.setRetryCount(currentRetry + 1);
            
            log.warn("🔄 메시지 재시도 스케줄링: messageId={}, retry={}/{}, delay={}초", 
                failedMessage.getMessageId(), currentRetry + 1, MAX_RETRY_COUNT, delaySeconds);
            
            // 지정된 시간 후 재시도 실행
            scheduler.schedule(() -> {
                try {
                    log.info("🔄 메시지 재시도 실행: messageId={}, retry={}", 
                        failedMessage.getMessageId(), failedMessage.getRetryCount());
                    retryAction.run();
                } catch (Exception retryError) {
                    log.error("❌ 재시도 실행 중 오류: messageId={}", 
                        failedMessage.getMessageId(), retryError);
                    // 재시도 중 오류 발생시 다시 재시도 스케줄링
                    scheduleRetry(failedMessage, retryError, retryAction);
                }
            }, delaySeconds, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            log.error("❌ 재시도 스케줄링 중 오류: messageId={}", failedMessage.getMessageId(), e);
        }
    }
    
    /**
     * 최대 재시도 횟수 초과시 처리
     */
    private void handleMaxRetryExceeded(ServerMessage message, Exception lastError) {
        try {
            log.error("💀 메시지 최종 실패: messageId={}, messageType={}, lastError={}", 
                message.getMessageId(), message.getMessageType(), lastError.getMessage());
            
            // 중요한 메시지 타입별 특별 처리
            switch (message.getMessageType()) {
                case PROCESS_SETTLEMENT:
                    handleCriticalSettlementFailure(message, lastError);
                    break;
                case CREATE_ROOM:
                    handleRoomCreationFailure(message, lastError);
                    break;
                default:
                    handleGeneralFailure(message, lastError);
            }
            
            // 실패 통계 업데이트
            incrementFailureMetrics(message.getMessageType().name());
            
        } catch (Exception e) {
            log.error("💥 최종 실패 처리 중 오류: messageId={}", message.getMessageId(), e);
        }
    }
    
    private void handleCriticalSettlementFailure(ServerMessage message, Exception error) {
        // 정산 실패는 치명적 - 관리자 알림 필요
        log.error("🚨 CRITICAL: 정산 처리 최종 실패 - 수동 개입 필요: messageId={}, payload={}", 
            message.getMessageId(), message.getPayload());
        
        // TODO: 관리자 알림 시스템 연동 (Slack, Email 등)
        // TODO: 실패한 정산 정보를 별도 저장소에 보관하여 수동 처리 대기
    }
    
    private void handleRoomCreationFailure(ServerMessage message, Exception error) {
        log.error("🏠 채팅방 생성 최종 실패: messageId={}, mealAppointmentId={}", 
            message.getMessageId(), message.getPayload().get("mealAppointmentId"));
        
        // TODO: 사용자에게 채팅방 생성 실패 알림
    }
    
    private void handleGeneralFailure(ServerMessage message, Exception error) {
        log.warn("⚠️ 일반 메시지 최종 실패: messageId={}, messageType={}", 
            message.getMessageId(), message.getMessageType());
    }
    
    private void incrementFailureMetrics(String messageType) {
        // TODO: 실패 메트릭 수집 시스템 연동
        log.debug("📊 실패 메트릭 업데이트: messageType={}", messageType);
    }
    
    /**
     * Circuit Breaker 상태 확인
     */
    public boolean isCircuitOpen(String targetServer) {
        // TODO: Circuit Breaker 로직 구현
        // 연속 실패 횟수가 임계값을 초과하면 잠시 요청 중단
        return false;
    }
    
    /**
     * 만료된 메시지 확인 및 정리
     */
    public void cleanupExpiredMessages(RedisTemplate<String, Object> redisTemplate) {
        try {
            // TODO: 만료 시간이 지난 미처리 메시지들을 정리
            log.debug("🧹 만료된 메시지 정리 작업 실행");
        } catch (Exception e) {
            log.error("❌ 만료 메시지 정리 중 오류", e);
        }
    }
}