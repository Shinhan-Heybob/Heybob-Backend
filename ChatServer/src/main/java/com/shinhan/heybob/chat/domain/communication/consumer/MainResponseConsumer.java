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
    
    @Scheduled(fixedDelay = 1000) // 1초마다 실행
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
                case CREATE_ROOM:
                    handleCreateRoom(message);
                    break;
                    
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
    
    private void handleCreateRoom(ServerMessage message) {
        try {
            Map<String, Object> payload = message.getPayload();
            String bob약Id = (String) payload.get("bob약Id");
            String creatorUserId = (String) payload.get("creatorUserId");
            String roomName = (String) payload.get("roomName");
            List<String> initialMembers = (List<String>) payload.get("initialMembers");
            
            log.info("📢 채팅방 생성 요청 수신: bob약Id={}, creator={}, roomName={}", 
                bob약Id, creatorUserId, roomName);
            
            // 채팅방 ID 생성 (실제로는 DB에 저장하고 ID를 받아야 함)
            Long chatRoomId = System.currentTimeMillis() % 1000000;
            
            // 응답 메시지 생성
            ServerMessage response = ServerMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .correlationId(message.getMessageId()) // 원본 메시지 ID를 correlationId로 설정
                .messageType(ServerMessage.MessageType.ROOM_CREATED)
                .sourceServer("CHAT")
                .targetServer("MAIN")
                .timestamp(LocalDateTime.now())
                .payload(Map.of(
                    "chatRoomId", chatRoomId,
                    "bob약Id", bob약Id,
                    "roomName", roomName,
                    "success", true,
                    "message", "채팅방이 성공적으로 생성되었습니다"
                ))
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
            
            // CHAT_TO_MAIN_STREAM으로 응답 전송
            Map<String, Object> streamData = convertToStreamData(response);
            redisTemplate.opsForStream().add("chat-to-main-stream", streamData);
            
            log.info("✅ 채팅방 생성 응답 전송: chatRoomId={}, correlationId={}", 
                chatRoomId, message.getMessageId());
                
        } catch (Exception e) {
            log.error("❌ 채팅방 생성 처리 실패: messageId={}", message.getMessageId(), e);
            
            // 에러 응답 전송
            sendErrorResponse(message.getMessageId(), "채팅방 생성 실패: " + e.getMessage());
        }
    }
    
    private Map<String, Object> convertToStreamData(ServerMessage message) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", message.getMessageId());
        data.put("correlationId", message.getCorrelationId());
        data.put("messageType", message.getMessageType().toString());
        data.put("sourceServer", message.getSourceServer());
        data.put("targetServer", message.getTargetServer());
        data.put("timestamp", message.getTimestamp().toString());
        data.put("payload", message.getPayload());
        data.put("retryCount", message.getRetryCount());
        if (message.getExpiryTime() != null) {
            data.put("expiryTime", message.getExpiryTime().toString());
        }
        return data;
    }
    
    private void sendErrorResponse(String correlationId, String errorMessage) {
        ServerMessage errorResponse = ServerMessage.builder()
            .messageId(java.util.UUID.randomUUID().toString())
            .correlationId(correlationId)
            .messageType(ServerMessage.MessageType.ERROR_RESPONSE)
            .sourceServer("CHAT")
            .targetServer("MAIN")
            .timestamp(LocalDateTime.now())
            .payload(Map.of(
                "success", false,
                "errorMessage", errorMessage
            ))
            .retryCount(0)
            .expiryTime(LocalDateTime.now().plusMinutes(5))
            .build();
        
        Map<String, Object> streamData = convertToStreamData(errorResponse);
        redisTemplate.opsForStream().add("chat-to-main-stream", streamData);
    }
}