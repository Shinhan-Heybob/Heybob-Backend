package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.repository.ChatRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatBatchServiceImpl implements ChatBatchService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatRepository chatRepository;
    
    @Override
    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    public void processStreamToMongoDB() {
        try {
            // 모든 방의 스트림 키 조회
            Set<String> streamKeys = redisTemplate.keys("room:messages:*");
            
            if (streamKeys == null || streamKeys.isEmpty()) {
                return;
            }
            
            log.info("Redis Stream 배치 처리 시작: 스트림 수={} (금융 메시지만 처리)", streamKeys.size());
            
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
                return;
            }
            
            List<ChatMessage> messagesToSave = new ArrayList<>();
            List<String> recordIdsToDelete = new ArrayList<>();
            
            for (var record : records) {
                try {
                    String recordId = record.getId().getValue();
                    Map<Object, Object> rawData = record.getValue();
                    
                    // 안전한 데이터 변환
                    String messageId = String.valueOf(rawData.get("messageId"));
                    String senderId = String.valueOf(rawData.get("senderId"));
                    String studentId = String.valueOf(rawData.get("studentId"));
                    String senderName = String.valueOf(rawData.get("senderName"));
                    String profileImageUrl = String.valueOf(rawData.get("profileImageUrl"));
                    String content = String.valueOf(rawData.get("content"));
                    String messageType = String.valueOf(rawData.get("messageType"));
                    String timestamp = String.valueOf(rawData.get("timestamp"));
                    
                    // ChatMessage 엔티티 생성
                    ChatMessage message = ChatMessage.builder()
                            .id(messageId)
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
            
            // MongoDB에 배치 저장
            if (!messagesToSave.isEmpty()) {
                for (ChatMessage message : messagesToSave) {
                    chatRepository.save(message);
                }
                
                log.info("MongoDB 배치 저장 완료 (금융 메시지): streamKey={}, count={}", streamKey, messagesToSave.size());
                
                // Redis에서 처리된 메시지 삭제
                for (String recordId : recordIdsToDelete) {
                    redisTemplate.opsForStream().delete(streamKey, recordId);
                }
                
                log.info("Redis Stream 정리 완료: streamKey={}, deleted={}", streamKey, recordIdsToDelete.size());
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