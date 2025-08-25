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
            // 필수 필드 검증 - 실패 시 ChatException 발생
            validateMessageFields(message);
            
            // 1차: Redis Stream 저장 시도
            Map<String, String> messageData = createMessageData(message);
            redisTemplate.opsForStream().add(streamKey, messageData);
            log.info("✅ Redis Stream 저장 성공: roomId={}, messageId={}", message.getRoomId(), message.getMessageId());
            fallbackMetrics.incrementRedisStreamSuccess();
            
        } catch (ChatException validationException) {
            // 필드 검증 실패 시 바로 MongoDB 저장
            log.warn("⚠️ 메시지 필드 검증 실패, MongoDB 직접 저장: {}", validationException.getMessage());
            try {
                saveDirectlyToMongoDB(message);
                log.info("✅ MongoDB 직접 저장 성공 (필드 검증 실패로 인한 fallback): roomId={}, messageId={}", 
                    message.getRoomId(), message.getMessageId());
                fallbackMetrics.incrementMongodbFallback();
            } catch (Exception mongoException) {
                log.error("💥 MongoDB 저장도 실패!: roomId={}, messageId={}", 
                    message.getRoomId(), message.getMessageId(), mongoException);
                fallbackMetrics.incrementTotalFailure();
                throw new ChatException(ErrorCode.MESSAGE_SAVE_FAILED, mongoException);
            }
            
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
    
    private void validateMessageFields(ChatMessageResponse message) {
        // 필수 필드가 비어있으면 ChatException 발생
        if (message.getMessageId() == null || message.getMessageId().trim().isEmpty()) {
            throw new ChatException(ErrorCode.INVALID_REQUEST, "messageId가 null이거나 비어있습니다");
        }
        if (message.getSenderId() == null || message.getSenderId().trim().isEmpty()) {
            throw new ChatException(ErrorCode.INVALID_REQUEST, "senderId가 null이거나 비어있습니다");
        }
        if (message.getStudentId() == null || message.getStudentId().trim().isEmpty()) {
            throw new ChatException(ErrorCode.INVALID_REQUEST, "studentId가 null이거나 비어있습니다");
        }
        if (message.getSenderName() == null || message.getSenderName().trim().isEmpty()) {
            throw new ChatException(ErrorCode.INVALID_REQUEST, "senderName이 null이거나 비어있습니다");
        }
        if (message.getProfileImageUrl() == null || message.getProfileImageUrl().trim().isEmpty()) {
            throw new ChatException(ErrorCode.INVALID_REQUEST, "profileImageUrl이 null이거나 비어있습니다");
        }
        if (message.getContent() == null || message.getContent().trim().isEmpty()) {
            throw new ChatException(ErrorCode.INVALID_REQUEST, "content가 null이거나 비어있습니다");
        }
        if (message.getMessageType() == null || message.getMessageType().trim().isEmpty()) {
            throw new ChatException(ErrorCode.INVALID_REQUEST, "messageType이 null이거나 비어있습니다");
        }
        if (message.getTimestamp() == null) {
            throw new ChatException(ErrorCode.INVALID_REQUEST, "timestamp가 null입니다");
        }
    }
    
    private Map<String, String> createMessageData(ChatMessageResponse message) {
        Map<String, String> messageData = new HashMap<>();
        // 검증이 이미 완료되었으므로 모든 필드가 존재함이 보장됨
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