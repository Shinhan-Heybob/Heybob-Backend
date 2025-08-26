package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.repository.ChatRepository;
import com.shinhan.heybob.chat.global.util.FallbackMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// 기본 Redis 작업만 사용 - Stream 복잡한 타입 제거
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatBatchServiceImpl implements ChatBatchService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatRepository chatRepository;
    private final FallbackMetrics fallbackMetrics;
    
    @Override
    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    public void processStreamToMongoDB() {
        try {
            // 모든 방의 스트림 키 조회
            Set<String> streamKeys = redisTemplate.keys("room:messages:*");
            
            if (streamKeys == null || streamKeys.isEmpty()) {
                return;
            }
            
            log.info("Redis Stream 배치 처리 시작: 스트림 수={}", streamKeys.size());
            
            for (String streamKey : streamKeys) {
                processRoomStream(streamKey);
            }
            
        } catch (Exception e) {
            log.error("Redis Stream 배치 처리 중 오류 발생", e);
        }
    }
    
    private void processRoomStream(String streamKey) {
        try {
            // 모든 메시지를 개별적으로 처리
            var records = redisTemplate.opsForStream().range(streamKey, org.springframework.data.domain.Range.unbounded());
            
            if (records == null || records.isEmpty()) {
                log.info("Redis Stream에 처리할 메시지가 없음: streamKey={}", streamKey);
                return;
            }
            
            log.info("Redis Stream에서 메시지 발견: streamKey={}, count={}", streamKey, records.size());
            
            List<ChatMessage> messagesToSave = new ArrayList<>();
            List<String> recordIdsToDelete = new ArrayList<>();
            
            for (var record : records) {
                try {
                    String recordId = record.getId().getValue();
                    Map<Object, Object> rawData = record.getValue();
                    
                    // 안전한 데이터 변환 (messageId가 null인 경우 UUID 생성)
                    Object messageIdObj = rawData.get("messageId");
                    String messageId = null;
                    if (messageIdObj != null && !"null".equals(String.valueOf(messageIdObj))) {
                        messageId = String.valueOf(messageIdObj);
                    } else {
                        messageId = UUID.randomUUID().toString();
                        log.warn("Redis Stream에서 messageId가 null이어서 UUID로 대체: recordId={}, newMessageId={}", recordId, messageId);
                    }
                    String senderId = String.valueOf(rawData.get("senderId"));
                    String studentId = String.valueOf(rawData.get("studentId"));
                    String senderName = String.valueOf(rawData.get("senderName"));
                    String profileImageUrl = String.valueOf(rawData.get("profileImageUrl"));
                    String content = String.valueOf(rawData.get("content"));
                    String messageType = String.valueOf(rawData.get("messageType"));
                    String timestamp = String.valueOf(rawData.get("timestamp"));
                    
                    // null 체크와 함께 ChatMessage 엔티티 생성
                    if (messageId == null || messageId.isEmpty() || "null".equals(messageId)) {
                        messageId = UUID.randomUUID().toString();
                        log.warn("messageId가 null이거나 비어있어서 UUID로 대체: {}", messageId);
                    }
                    
                    ChatMessage message = ChatMessage.builder()
                            .id(messageId)  // MongoDB _id (messageId 역할)
                            .roomId(extractRoomIdFromStreamKey(streamKey))
                            .senderId(senderId)
                            .studentId(studentId)
                            .senderName(senderName)
                            .profileImageUrl(profileImageUrl)
                            .content(content)
                            .messageType(ChatMessage.MessageType.valueOf(messageType))
                            .timestamp(LocalDateTime.parse(timestamp))
                            .build();
                    
                    messagesToSave.add(message);
                    recordIdsToDelete.add(recordId);
                    
                } catch (Exception e) {
                    log.warn("메시지 변환 실패: streamKey={}", streamKey, e);
                }
            }
            
            // MongoDB에 배치 저장 (Exception 처리 개선)
            if (!messagesToSave.isEmpty()) {
                List<String> successfulRecordIds = new ArrayList<>();
                List<String> failedRecordIds = new ArrayList<>();
                
                for (int i = 0; i < messagesToSave.size(); i++) {
                    ChatMessage message = messagesToSave.get(i);
                    String recordId = recordIdsToDelete.get(i);
                    
                    try {
                        chatRepository.save(message);
                        successfulRecordIds.add(recordId);
                        log.debug("MongoDB 저장 성공: messageId={}", message.getId());
                        
                    } catch (Exception e) {
                        failedRecordIds.add(recordId);
                        log.warn("MongoDB 저장 실패 (스킵): messageId={}, recordId={}, error={}", 
                            message.getId(), recordId, e.getMessage());
                        
                        // 메트릭 업데이트
                        fallbackMetrics.incrementTotalFailure();
                    }
                }
                
                // 성공한 메시지만 Redis에서 삭제
                if (!successfulRecordIds.isEmpty()) {
                    for (String recordId : successfulRecordIds) {
                        try {
                            redisTemplate.opsForStream().delete(streamKey, recordId);
                        } catch (Exception e) {
                            log.warn("Redis 메시지 삭제 실패: recordId={}, error={}", recordId, e.getMessage());
                        }
                    }
                    log.info("MongoDB 저장 및 Redis 정리 완료: streamKey={}, success={}, failed={}", 
                        streamKey, successfulRecordIds.size(), failedRecordIds.size());
                } else {
                    log.warn("모든 메시지 저장 실패: streamKey={}, totalFailed={}", streamKey, messagesToSave.size());
                }
            }
            
        } catch (Exception e) {
            log.error("스트림 처리 중 오류: streamKey={}", streamKey, e);
        }
    }
    
    private String extractRoomIdFromStreamKey(String streamKey) {
        // "room:messages:123" → "123"
        return streamKey.replace("room:messages:", "");
    }
}