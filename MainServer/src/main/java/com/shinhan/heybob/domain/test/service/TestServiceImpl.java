package com.shinhan.heybob.domain.test.service;

import com.shinhan.heybob.domain.notification.dto.ChatBroadcastRequest;
import com.shinhan.heybob.domain.notification.dto.ServerMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestServiceImpl implements TestService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String MAIN_TO_CHAT_STREAM = "main-to-chat-stream";
    
    @Override
    public String sendSettlementBroadcast(ChatBroadcastRequest request) {
        try {
            String messageId = UUID.randomUUID().toString();
            
            // 정산 브로드캐스트 메시지 생성
            Map<String, Object> payload = new HashMap<>();
            payload.put("settlementId", request.getSettlementId());
            payload.put("roomId", request.getRoomId());
            payload.put("requesterId", request.getRequesterId());
            payload.put("requesterName", request.getRequesterName());
            payload.put("requesterStudentId", request.getRequesterStudentId());
            payload.put("requesterProfileImg", request.getRequesterProfileImg());
            payload.put("requestAmount", request.getRequestAmount());
            payload.put("message", request.getMessage());
            
            ServerMessage message = ServerMessage.builder()
                .messageId(messageId)
                .messageType(ServerMessage.MessageType.PAYMENT_REQUEST)
                .sourceServer("MAIN")
                .targetServer("CHAT")
                .timestamp(LocalDateTime.now())
                .payload(payload)
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
            
            // Redis Stream으로 메시지 전송
            Map<String, Object> streamData = convertToStreamData(message);
            log.info("🔍 Redis Stream 전송 데이터: {}", streamData);
            
            redisTemplate.opsForStream().add(MAIN_TO_CHAT_STREAM, streamData);
            
            log.info("✅ 정산 브로드캐스트 전송 완료: messageId={}, settlementId={}", 
                messageId, request.getSettlementId());
            
            return messageId;
            
        } catch (Exception e) {
            log.error("❌ 정산 브로드캐스트 전송 실패: settlementId={}", request.getSettlementId(), e);
            throw new RuntimeException("정산 브로드캐스트 전송 실패: " + e.getMessage());
        }
    }
    
    @Override
    public String sendSavingsBroadcast(ChatBroadcastRequest request) {
        try {
            String messageId = UUID.randomUUID().toString();
            
            // 적금 브로드캐스트 메시지 생성
            Map<String, Object> payload = new HashMap<>();
            payload.put("settlementId", request.getSettlementId());
            payload.put("roomId", request.getRoomId());
            payload.put("requesterId", request.getRequesterId());
            payload.put("requesterName", request.getRequesterName());
            payload.put("requesterStudentId", request.getRequesterStudentId());
            payload.put("requesterProfileImg", request.getRequesterProfileImg());
            payload.put("requestAmount", request.getRequestAmount());
            payload.put("message", request.getMessage());
            
            ServerMessage message = ServerMessage.builder()
                .messageId(messageId)
                .messageType(ServerMessage.MessageType.SAVINGS_REQUEST)
                .sourceServer("MAIN")
                .targetServer("CHAT")
                .timestamp(LocalDateTime.now())
                .payload(payload)
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
            
            // Redis Stream으로 메시지 전송
            Map<String, Object> streamData = convertToStreamData(message);
            log.info("🔍 Redis Stream 전송 데이터: {}", streamData);
            
            redisTemplate.opsForStream().add(MAIN_TO_CHAT_STREAM, streamData);
            
            log.info("✅ 적금 브로드캐스트 전송 완료: messageId={}, settlementId={}", 
                messageId, request.getSettlementId());
            
            return messageId;
            
        } catch (Exception e) {
            log.error("❌ 적금 브로드캐스트 전송 실패: settlementId={}", request.getSettlementId(), e);
            throw new RuntimeException("적금 브로드캐스트 전송 실패: " + e.getMessage());
        }
    }
    
    private Map<String, Object> convertToStreamData(ServerMessage message) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", message.getMessageId());
        data.put("messageType", message.getMessageType().name());
        data.put("sourceServer", message.getSourceServer());
        data.put("targetServer", message.getTargetServer());
        data.put("timestamp", message.getTimestamp().toString());
        data.put("retryCount", message.getRetryCount());
        data.put("expiryTime", message.getExpiryTime().toString());
        
        // payload 데이터를 payload_ 접두사와 함께 추가
        if (message.getPayload() != null) {
            for (Map.Entry<String, Object> entry : message.getPayload().entrySet()) {
                data.put("payload_" + entry.getKey(), entry.getValue());
            }
        }
        
        return data;
    }
}