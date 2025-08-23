package com.shinhan.heybob.chat.domain.communication.consumer;

import com.shinhan.heybob.chat.domain.communication.dto.ServerMessage;
import com.shinhan.heybob.chat.domain.communication.service.MainServerCommunicationServiceImpl;
import com.shinhan.heybob.chat.domain.communication.handler.MessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MainResponseConsumer {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final MainServerCommunicationServiceImpl communicationService;
    private final MessageHandler messageHandler;
    
    private static final String MAIN_TO_CHAT_STREAM = "main-to-chat-stream";
    private static final String CONSUMER_GROUP = "chat-server-group";
    private static final String CONSUMER_NAME = "chat-server-consumer";
    
    // @Scheduled(fixedDelay = 1000) // 1초마다 실행 - MainServer 연동 전까지 비활성화
    public void consumeMessages() {
        try {
            // Consumer Group이 없으면 생성
            ensureConsumerGroupExists();
            
            // 메시지 읽기
            List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                .read(
                    org.springframework.data.redis.connection.stream.Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                    StreamOffset.create(MAIN_TO_CHAT_STREAM, org.springframework.data.redis.connection.stream.ReadOffset.lastConsumed())
                );
            
            if (messages != null && !messages.isEmpty()) {
                log.debug("📨 Main 서버로부터 {} 개 메시지 수신", messages.size());
                
                for (MapRecord<String, Object, Object> record : messages) {
                    try {
                        processMessage(record);
                        
                        // 메시지 처리 완료 후 ACK
                        redisTemplate.opsForStream().acknowledge(MAIN_TO_CHAT_STREAM, CONSUMER_GROUP, record.getId());
                        
                    } catch (Exception e) {
                        log.error("❌ 메시지 처리 실패: recordId={}", record.getId(), e);
                        // TODO: 에러 메시지 처리 로직 (재시도 큐 등)
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Main 서버 메시지 소비 중 오류", e);
        }
    }
    
    private void processMessage(MapRecord<String, Object, Object> record) {
        try {
            // Stream 데이터를 ServerMessage로 변환
            ServerMessage message = convertFromStreamData(record.getValue());
            
            log.info("📨 Main 서버 메시지 수신: messageType={}, messageId={}", 
                message.getMessageType(), message.getMessageId());
            
            // 메시지 타입별 처리
            switch (message.getMessageType()) {
                case ROOM_CREATED:
                case ROOM_JOINED:
                case ROOM_MEMBERS_RESPONSE:
                case SETTLEMENT_PROCESSED:
                case USER_ACCESS_RESPONSE:
                    // 응답 메시지는 대기 중인 Future에 전달
                    communicationService.handleResponse(message);
                    break;
                    
                case ROOM_STATUS_CHANGED:
                case MEMBER_JOINED:
                case MEMBER_LEFT:
                case SETTLEMENT_COMPLETED:
                    // 알림 메시지는 별도 핸들러에서 처리
                    messageHandler.handleNotification(message);
                    break;
                    
                case BROADCAST_SETTLEMENT_REQUEST:
                    // Main 서버의 정산 브로드캐스트 요청 처리
                    messageHandler.handleSettlementBroadcast(message);
                    break;
                    
                case ERROR_RESPONSE:
                    handleErrorResponse(message);
                    break;
                    
                case HEARTBEAT:
                    handleHeartbeat(message);
                    break;
                    
                default:
                    log.warn("⚠️ 알 수 없는 메시지 타입: {}", message.getMessageType());
            }
            
        } catch (Exception e) {
            log.error("❌ 메시지 처리 중 오류: recordId={}", record.getId(), e);
            throw e;
        }
    }
    
    private ServerMessage convertFromStreamData(Map<Object, Object> streamData) {
        // Stream 데이터를 ServerMessage 객체로 변환
        Map<String, Object> payload = new HashMap<>();
        
        // payload_ 접두사가 붙은 필드들을 추출
        for (Map.Entry<Object, Object> entry : streamData.entrySet()) {
            String key = entry.getKey().toString();
            if (key.startsWith("payload_")) {
                String payloadKey = key.substring("payload_".length());
                payload.put(payloadKey, entry.getValue());
            }
        }
        
        return ServerMessage.builder()
                .messageId(getString(streamData, "messageId"))
                .correlationId(getString(streamData, "correlationId"))
                .messageType(ServerMessage.MessageType.valueOf(getString(streamData, "messageType")))
                .sourceServer(getString(streamData, "sourceServer"))
                .targetServer(getString(streamData, "targetServer"))
                .timestamp(LocalDateTime.parse(getString(streamData, "timestamp")))
                .payload(payload)
                .retryCount(getInteger(streamData, "retryCount"))
                .expiryTime(getString(streamData, "expiryTime") != null ? 
                    LocalDateTime.parse(getString(streamData, "expiryTime")) : null)
                .build();
    }
    
    private void handleErrorResponse(ServerMessage message) {
        log.error("💥 Main 서버 에러 응답: messageId={}, payload={}", 
            message.getMessageId(), message.getPayload());
        
        // 대기 중인 Future에 에러 전달
        communicationService.handleResponse(message);
    }
    
    private void handleHeartbeat(ServerMessage message) {
        log.debug("💓 Main 서버 Heartbeat 수신: {}", message.getTimestamp());
        // TODO: 헬스체크 상태 업데이트
    }
    
    private void ensureConsumerGroupExists() {
        try {
            redisTemplate.opsForStream().createGroup(MAIN_TO_CHAT_STREAM, 
                org.springframework.data.redis.connection.stream.ReadOffset.from("0"), CONSUMER_GROUP);
        } catch (Exception e) {
            // Consumer Group이 이미 존재하면 무시
            if (!e.getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer Group 생성 결과: {}", e.getMessage());
            }
        }
    }
    
    private String getString(Map<Object, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
    
    private Integer getInteger(Map<Object, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}