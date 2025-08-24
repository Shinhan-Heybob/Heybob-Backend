package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.repository.ChatRepository;
import com.shinhan.heybob.chat.global.error.ChatException;
import com.shinhan.heybob.chat.global.error.ErrorCode;
import com.shinhan.heybob.chat.global.util.FallbackMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatStreamServiceImpl implements ChatStreamService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatRepository chatRepository;
    private final FallbackMetrics fallbackMetrics;
    
    @Override
    public void saveToStream(ChatMessageResponse message) {
        String streamKey = "room:messages:" + message.getRoomId();
        
        try {
            // 1차: Redis Stream 저장 시도
            Map<String, Object> messageData = createMessageData(message);
            redisTemplate.opsForStream().add(streamKey, messageData);
            log.info("✅ Redis Stream 저장 성공: roomId={}, messageId={}", message.getRoomId(), message.getMessageId());
            fallbackMetrics.incrementRedisStreamSuccess();
            
        } catch (Exception redisException) {
            log.error("❌ Redis Stream 저장 실패, MongoDB Fallback 시도: roomId={}, messageId={}", 
                message.getRoomId(), message.getMessageId(), redisException);
            
            try {
                // 2차: MongoDB 직접 저장 (Fallback)
                saveDirectlyToMongoDB(message);
                log.warn("⚠️ MongoDB Fallback 저장 성공: roomId={}, messageId={}", message.getRoomId(), message.getMessageId());
                fallbackMetrics.incrementMongodbFallback();
                
            } catch (Exception mongoException) {
                log.error("💥 MongoDB Fallback도 실패! 금융 메시지 유실: roomId={}, messageId={}", 
                    message.getRoomId(), message.getMessageId(), mongoException);
                fallbackMetrics.incrementTotalFailure();
                throw new ChatException(ErrorCode.MESSAGE_SAVE_FAILED, mongoException);
            }
        }
    }
    
    private Map<String, Object> createMessageData(ChatMessageResponse message) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("messageId", message.getMessageId());
        messageData.put("senderId", message.getSenderId());
        messageData.put("studentId", message.getStudentId());
        messageData.put("senderName", message.getSenderName());
        messageData.put("profileImageUrl", message.getProfileImageUrl());
        messageData.put("content", message.getContent());
        messageData.put("messageType", message.getMessageType());
        messageData.put("timestamp", message.getTimestamp().toString());
        return messageData;
    }
    
    private void saveDirectlyToMongoDB(ChatMessageResponse response) {
        String messageId = response.getMessageId();
        if (messageId == null || messageId.trim().isEmpty()) {
            messageId = java.util.UUID.randomUUID().toString();
            log.warn("ChatStreamService Fallback: MessageId가 null이었습니다. 새로 생성: {}", messageId);
        }
        
        ChatMessage message = ChatMessage.builder()
                .id(messageId)  // MongoDB _id (messageId 역할)
                .roomId(response.getRoomId())
                .senderId(response.getSenderId())
                .studentId(response.getStudentId())
                .senderName(response.getSenderName())
                .profileImageUrl(response.getProfileImageUrl())
                .content(response.getContent())
                .messageType(ChatMessage.MessageType.valueOf(response.getMessageType()))
                .timestamp(response.getTimestamp())
                .paymentRequestData(response.getPaymentRequestData())
                .paymentCompleteData(response.getPaymentCompleteData())
                .emergencyFallback(true)  // Fallback으로 저장됐음을 표시
                .build();
                
        chatRepository.save(message);
    }
}