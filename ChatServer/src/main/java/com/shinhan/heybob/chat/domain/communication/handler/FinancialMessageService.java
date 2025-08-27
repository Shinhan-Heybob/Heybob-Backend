package com.shinhan.heybob.chat.domain.communication.handler;

import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialMessageService {
    
    private final ChatService chatService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    
    private static final String FAILED_FINANCIAL_MESSAGES_STREAM = "failed-financial-messages";
    
    /**
     * 금융 메시지를 안전하게 저장 (1번 실패 시 즉시 Redis 백업)
     */
    public boolean saveFinancialMessageSafely(ChatMessage chatMessage) {
        String messageId = chatMessage.getId();
        
        try {
            // MongoDB에 1번 저장 시도
            chatService.saveMessage(chatMessage);
            log.info("✅ 금융 메시지 MongoDB 저장 성공: messageId={}", messageId);
            
            // 성공 로그 기록
            log.info("✅ 금융 메시지 저장 성공");
            return true;
            
        } catch (Exception e) {
            log.warn("❌ MongoDB 저장 실패, Redis Stream 백업 시도: messageId={}, error={}", 
                    messageId, e.getMessage());
            
            // 즉시 Redis Stream에 백업
            boolean backedUpToRedis = backupToRedisStream(chatMessage, e.getMessage());
            
            // 실패 이벤트 발행 (알림용)
            publishFailureEvent(chatMessage, e.getMessage(), 1, backedUpToRedis);
            
            // Redis 백업 성공하면 일단 OK (배치가 나중에 복구)
            return backedUpToRedis;
        }
    }
    
    /**
     * 실패한 금융 메시지를 Redis Stream에 백업
     */
    private boolean backupToRedisStream(ChatMessage chatMessage, String failureReason) {
        try {
            Map<String, Object> backupData = createBackupData(chatMessage, failureReason);
            
            String recordId = redisTemplate.opsForStream()
                    .add(FAILED_FINANCIAL_MESSAGES_STREAM, backupData).getValue();
                    
            log.warn("🔄 금융 메시지 Redis Stream 백업 성공: messageId={}, recordId={}", 
                    chatMessage.getId(), recordId);
            return true;
            
        } catch (Exception e) {
            log.error("💥 Redis Stream 백업도 실패!: messageId={}, error={}", 
                    chatMessage.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    private Map<String, Object> createBackupData(ChatMessage chatMessage, String failureReason) {
        Map<String, Object> data = new HashMap<>();
        
        // 기본 메시지 정보
        data.put("messageId", chatMessage.getId());
        data.put("roomId", chatMessage.getRoomId());
        data.put("senderId", chatMessage.getSenderId());
        data.put("studentId", chatMessage.getStudentId());
        data.put("senderName", chatMessage.getSenderName());
        data.put("profileImageUrl", chatMessage.getProfileImageUrl());
        data.put("content", chatMessage.getContent());
        data.put("messageType", chatMessage.getMessageType().name());
        data.put("timestamp", chatMessage.getTimestamp().toString());
        
        // 금융 메시지 특화 데이터
        if (chatMessage.getPaymentRequestData() != null) {
            data.put("paymentRequestData", chatMessage.getPaymentRequestData());
        }
        if (chatMessage.getPaymentCompleteData() != null) {
            data.put("paymentCompleteData", chatMessage.getPaymentCompleteData());
        }
        
        // 백업 메타데이터
        data.put("backupTime", LocalDateTime.now().toString());
        data.put("failureReason", failureReason);
        data.put("retryCount", 1);
        
        return data;
    }
    
    /**
     * 실패 이벤트 발행 (알림 시스템으로 전달)
     */
    private void publishFailureEvent(ChatMessage chatMessage, String failureReason, 
                                   int attemptCount, boolean isBackedUpToRedis) {
        
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("settlementId", 
            chatMessage.getPaymentRequestData() != null ? 
                chatMessage.getPaymentRequestData().getSettlementId() : "unknown");
        messageData.put("requesterName", 
            chatMessage.getPaymentRequestData() != null ? 
                chatMessage.getPaymentRequestData().getRequesterName() : "unknown");
        messageData.put("requestAmount", 
            chatMessage.getPaymentRequestData() != null ? 
                chatMessage.getPaymentRequestData().getRequestAmount() : 0);
        
        FinancialMessageFailureEvent event = FinancialMessageFailureEvent.builder()
                .messageId(chatMessage.getId())
                .roomId(chatMessage.getRoomId())
                .messageType(chatMessage.getMessageType().name())
                .failureReason(failureReason)
                .attemptCount(attemptCount)
                .failureTime(LocalDateTime.now())
                .messageData(messageData)
                .isBackedUpToRedis(isBackedUpToRedis)
                .build();
                
        eventPublisher.publishEvent(event);
        log.info("📡 금융 메시지 실패 이벤트 발행: messageId={}", chatMessage.getId());
    }
}