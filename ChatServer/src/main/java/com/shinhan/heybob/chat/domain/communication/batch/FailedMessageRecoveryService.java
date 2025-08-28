package com.shinhan.heybob.chat.domain.communication.batch;

import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FailedMessageRecoveryService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatService chatService;
    
    private static final String FAILED_FINANCIAL_MESSAGES_STREAM = "failed-financial-messages";
    private static final String RECOVERY_CONSUMER_GROUP = "recovery-group";
    private static final String RECOVERY_CONSUMER = "recovery-consumer";
    
    /**
     * 10분마다 실행 - Redis Stream에서 실패한 메시지 복구 시도
     */
    @Scheduled(fixedDelay = 600000) // 10분마다
    public void recoverFailedMessages() {
        try {
            // Consumer Group 초기화 (최초 실행 시)
            ensureConsumerGroupExists();
            
            // Redis Stream에서 실패한 메시지들 읽기
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .read(
                    org.springframework.data.redis.connection.stream.Consumer.from(RECOVERY_CONSUMER_GROUP, RECOVERY_CONSUMER),
                    StreamOffset.create(FAILED_FINANCIAL_MESSAGES_STREAM, 
                        org.springframework.data.redis.connection.stream.ReadOffset.lastConsumed())
                );
            
            if (records == null || records.isEmpty()) {
                log.debug("🔍 복구할 실패 메시지 없음 (10분 주기 체크)");
                return;
            }
            
            log.info("🔄 Redis Stream에서 {}건의 실패 메시지 복구 시도 (10분 주기)", records.size());
            
            int successCount = 0;
            int failCount = 0;
            
            for (MapRecord<String, Object, Object> record : records) {
                try {
                    boolean recovered = recoverSingleMessage(record);
                    
                    if (recovered) {
                        // 복구 성공 시 ACK 및 삭제
                        redisTemplate.opsForStream().acknowledge(
                            FAILED_FINANCIAL_MESSAGES_STREAM, RECOVERY_CONSUMER_GROUP, record.getId());
                        redisTemplate.opsForStream().delete(FAILED_FINANCIAL_MESSAGES_STREAM, record.getId());
                        
                        successCount++;
                        log.info("✅ 메시지 복구 성공: recordId={}, messageId={}", 
                            record.getId(), record.getValue().get("messageId"));
                    } else {
                        // 복구 실패 시에도 ACK (무한 재시도 방지)
                        redisTemplate.opsForStream().acknowledge(
                            FAILED_FINANCIAL_MESSAGES_STREAM, RECOVERY_CONSUMER_GROUP, record.getId());
                        failCount++;
                    }
                    
                } catch (Exception e) {
                    log.error("❌ 메시지 복구 중 오류: recordId={}, error={}", 
                        record.getId(), e.getMessage());
                    
                    // 오류 발생 시에도 ACK
                    redisTemplate.opsForStream().acknowledge(
                        FAILED_FINANCIAL_MESSAGES_STREAM, RECOVERY_CONSUMER_GROUP, record.getId());
                    failCount++;
                }
            }
            
            log.info("🔄 메시지 복구 완료 (10분 주기): 성공 {}건, 실패 {}건", successCount, failCount);
            
        } catch (Exception e) {
            log.error("❌ 실패 메시지 복구 배치 작업 중 오류", e);
        }
    }
    
    private boolean recoverSingleMessage(MapRecord<String, Object, Object> record) {
        try {
            Map<Object, Object> data = record.getValue();
            
            // Redis Stream 데이터를 ChatMessage 객체로 복원
            ChatMessage chatMessage = restoreChatMessageFromRedis(data);
            
            if (chatMessage == null) {
                log.error("❌ ChatMessage 복원 실패: recordId={}", record.getId());
                return false;
            }
            
            // MongoDB에 저장 시도
            chatService.saveMessage(chatMessage);
            
            // 성공 로그 기록
            
            log.info("✅ 복구 성공: messageId={}, type={}", 
                chatMessage.getId(), chatMessage.getMessageType());
            return true;
            
        } catch (Exception e) {
            log.warn("❌ 메시지 복구 실패: recordId={}, error={}", record.getId(), e.getMessage());
            return false;
        }
    }
    
    private ChatMessage restoreChatMessageFromRedis(Map<Object, Object> data) {
        try {
            return ChatMessage.builder()
                .id(getString(data, "messageId"))
                .roomId(getString(data, "roomId"))
                .senderId(getString(data, "senderId"))
                .studentId(getString(data, "studentId"))
                .senderName(getString(data, "senderName"))
                .profileImageUrl(getString(data, "profileImageUrl"))
                .content(getString(data, "content"))
                .messageType(ChatMessage.MessageType.valueOf(getString(data, "messageType")))
                .timestamp(LocalDateTime.parse(getString(data, "timestamp")))
                .paymentRequestData(data.get("paymentRequestData") != null ? 
                    (com.shinhan.heybob.chat.domain.chat.dto.PaymentRequestData) data.get("paymentRequestData") : null)
                .paymentCompleteData(data.get("paymentCompleteData") != null ? 
                    (com.shinhan.heybob.chat.domain.chat.dto.PaymentCompleteData) data.get("paymentCompleteData") : null)
                .emergencyFallback(true)  // 복구된 메시지임을 표시
                .build();
                
        } catch (Exception e) {
            log.error("❌ ChatMessage 복원 중 오류: {}", e.getMessage());
            return null;
        }
    }
    
    private void ensureConsumerGroupExists() {
        try {
            // Stream이 존재하지 않으면 아무것도 하지 않음
            Boolean streamExists = redisTemplate.hasKey(FAILED_FINANCIAL_MESSAGES_STREAM);
            if (Boolean.FALSE.equals(streamExists)) {
                return;
            }
            
            // Consumer Group 생성 시도
            redisTemplate.opsForStream().createGroup(
                FAILED_FINANCIAL_MESSAGES_STREAM, 
                org.springframework.data.redis.connection.stream.ReadOffset.from("0"), 
                RECOVERY_CONSUMER_GROUP
            );
            log.debug("✅ Recovery Consumer Group 생성: {}", RECOVERY_CONSUMER_GROUP);
            
        } catch (Exception e) {
            // Consumer Group이 이미 존재하면 에러 무시
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.debug("Recovery Consumer Group 이미 존재: {}", RECOVERY_CONSUMER_GROUP);
            } else {
                log.warn("Recovery Consumer Group 생성 실패: {}", e.getMessage());
            }
        }
    }
    
    private String getString(Map<Object, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString().replaceAll("^\"|\"$", "") : null;
    }
    
    /**
     * 수동 복구 메서드 (관리자가 호출 가능)
     */
    public int manualRecovery() {
        log.info("🔧 수동 복구 시작");
        recoverFailedMessages();
        return 0; // 복구된 메시지 수 반환 (구현 시)
    }
}