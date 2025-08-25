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
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.RedisSystemException;

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
    
    @PostConstruct
    public void initializeConsumerGroup() {
        try {
            // Stream이 없으면 생성
            Boolean exists = redisTemplate.hasKey(MAIN_TO_CHAT_STREAM);
            if (Boolean.FALSE.equals(exists)) {
                redisTemplate.opsForStream().add(MAIN_TO_CHAT_STREAM, Map.of("init", "stream"));
                log.info("✅ Redis Stream 초기화: {}", MAIN_TO_CHAT_STREAM);
            }
            
            // 기존 Consumer Group 삭제 (개발 환경에서만)
            try {
                redisTemplate.opsForStream().destroyGroup(MAIN_TO_CHAT_STREAM, CONSUMER_GROUP);
                log.info("🗑️ 기존 Consumer Group 삭제: {}", CONSUMER_GROUP);
            } catch (Exception e) {
                log.debug("Consumer Group이 존재하지 않음 (정상): {}", CONSUMER_GROUP);
            }
            
            // Consumer Group 생성 (처음부터 읽기: 0)
            redisTemplate.opsForStream().createGroup(MAIN_TO_CHAT_STREAM, 
                org.springframework.data.redis.connection.stream.ReadOffset.from("0"), CONSUMER_GROUP);
            log.info("✅ Consumer Group 생성 완료 (처음부터 읽기): {}", CONSUMER_GROUP);
            
        } catch (RedisSystemException e) {
            // BUSYGROUP: Consumer Group이 이미 존재
            if (e.getCause() != null && e.getCause().getMessage() != null 
                && e.getCause().getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer Group이 이미 존재: {}", CONSUMER_GROUP);
            } else {
                log.error("❌ Consumer Group 초기화 실패", e);
            }
        } catch (Exception e) {
            log.error("❌ Stream 초기화 중 예상치 못한 오류", e);
        }
    }
    
    @Scheduled(fixedDelay = 1000) // 1초마다 실행
    public void consumeMessages() {
        try {
            // 메시지 읽기
            List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                .read(
                    org.springframework.data.redis.connection.stream.Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                    StreamOffset.create(MAIN_TO_CHAT_STREAM, org.springframework.data.redis.connection.stream.ReadOffset.lastConsumed())
                );
            
            // 항상 로그 출력 (디버깅용)
            log.info("🔍 Redis Stream 읽기 결과: messages={}", messages != null ? messages.size() : "null");
            
            if (messages != null && !messages.isEmpty()) {
                log.info("📨 Main 서버로부터 {} 개 메시지 수신", messages.size());
                
                for (MapRecord<String, Object, Object> record : messages) {
                    try {
                        log.info("📋 처리할 메시지: recordId={}, data={}", record.getId(), record.getValue());
                        processMessage(record);
                        
                        // 메시지 처리 완료 후 ACK
                        redisTemplate.opsForStream().acknowledge(MAIN_TO_CHAT_STREAM, CONSUMER_GROUP, record.getId());
                        log.info("✅ 메시지 처리 완료: recordId={}", record.getId());
                        
                        // 처리된 메시지 삭제 (Stream에서 완전 제거)
                        redisTemplate.opsForStream().delete(MAIN_TO_CHAT_STREAM, record.getId());
                        log.debug("🗑️ 처리된 메시지 삭제: recordId={}", record.getId());
                        
                    } catch (Exception e) {
                        log.error("❌ 메시지 처리 실패: recordId={}, data={}", record.getId(), record.getValue(), e);
                        // 에러 발생 시에도 ACK (무한 재시도 방지)
                        redisTemplate.opsForStream().acknowledge(MAIN_TO_CHAT_STREAM, CONSUMER_GROUP, record.getId());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Main 서버 메시지 소비 중 오류", e);
        }
    }
    
    /**
     * 10분마다 오래된 메시지들 대량 정리
     */
    @Scheduled(fixedDelay = 600000) // 10분마다 실행
    public void cleanupOldMessages() {
        try {
            // 현재 시간 - 1시간 이전 메시지들 삭제
            long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
            String maxId = oneHourAgo + "-0";
            
            // XTRIM으로 오래된 메시지들 정리 (최근 100개 메시지만 유지)
            Long trimmed = redisTemplate.opsForStream().trim(MAIN_TO_CHAT_STREAM, 100);
            if (trimmed != null && trimmed > 0) {
                log.info("🧹 오래된 메시지 {}개 정리 완료 (최근 100개 유지)", trimmed);
            }
            
        } catch (Exception e) {
            log.warn("⚠️ 메시지 정리 중 오류 (무시 가능): {}", e.getMessage());
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
        try {
            log.info("🔍 Stream 데이터 변환 시작: {}", streamData);
            
            // Stream 데이터를 ServerMessage 객체로 변환
            Map<String, Object> payload = new HashMap<>();
            
            // payload_ 접두사가 붙은 필드들을 추출
            for (Map.Entry<Object, Object> entry : streamData.entrySet()) {
                String key = entry.getKey().toString();
                if (key.startsWith("payload_")) {
                    String payloadKey = key.substring("payload_".length());
                    Object value = entry.getValue();
                    
                    // Payload 값도 문자열인 경우 따옴표 제거
                    if (value instanceof String) {
                        value = ((String) value).replaceAll("^\"|\"$", "");
                    }
                    
                    payload.put(payloadKey, value);
                }
            }
            
            // 필수 필드들을 안전하게 추출
            String messageId = getString(streamData, "messageId");
            String messageTypeStr = getString(streamData, "messageType");
            String sourceServer = getString(streamData, "sourceServer");
            String targetServer = getString(streamData, "targetServer");
            String timestampStr = getString(streamData, "timestamp");
            
            
            log.info("📋 추출된 필드들: messageId={}, messageType={}, sourceServer={}, targetServer={}, timestamp={}", 
                messageId, messageTypeStr, sourceServer, targetServer, timestampStr);
            
            // 필수 필드 검증 및 기본값 설정
            if (messageId == null || messageId.isEmpty()) {
                messageId = "unknown-" + System.currentTimeMillis();
                log.warn("⚠️ messageId 누락, 기본값 설정: {}", messageId);
            }
            
            if (messageTypeStr == null || messageTypeStr.isEmpty()) {
                throw new IllegalArgumentException("messageType은 필수 필드입니다");
            }
            
            if (sourceServer == null || sourceServer.isEmpty()) {
                sourceServer = "MAIN";  // 기본값
                log.warn("⚠️ sourceServer 누락, 기본값 설정: {}", sourceServer);
            }
            
            if (targetServer == null || targetServer.isEmpty()) {
                targetServer = "CHAT";  // 기본값
                log.warn("⚠️ targetServer 누락, 기본값 설정: {}", targetServer);
            }
            
            LocalDateTime timestamp;
            if (timestampStr == null || timestampStr.isEmpty()) {
                timestamp = LocalDateTime.now();  // 기본값
                log.warn("⚠️ timestamp 누락, 현재 시간 사용: {}", timestamp);
            } else {
                try {
                    timestamp = LocalDateTime.parse(timestampStr);
                } catch (Exception e) {
                    timestamp = LocalDateTime.now();
                    log.warn("⚠️ timestamp 파싱 실패, 현재 시간 사용: {} -> {}", timestampStr, timestamp);
                }
            }
            
            ServerMessage result = ServerMessage.builder()
                    .messageId(messageId)
                    .correlationId(getString(streamData, "correlationId"))
                    .messageType(ServerMessage.MessageType.valueOf(messageTypeStr))
                    .sourceServer(sourceServer)
                    .targetServer(targetServer)
                    .timestamp(timestamp)
                    .payload(payload)
                    .retryCount(getInteger(streamData, "retryCount"))
                    .expiryTime(getString(streamData, "expiryTime") != null ? 
                        LocalDateTime.parse(getString(streamData, "expiryTime")) : null)
                    .build();
                    
            log.info("✅ ServerMessage 변환 완료: {}", result);
            return result;
            
        } catch (Exception e) {
            log.error("❌ Stream 데이터 변환 실패: streamData={}", streamData, e);
            throw new RuntimeException("Stream 데이터 변환 실패", e);
        }
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
    
    
    private String getString(Map<Object, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        
        String stringValue = value.toString();
        // Redis Stream에서 문자열이 따옴표로 감싸져 올 수 있으므로 제거
        return stringValue.replaceAll("^\"|\"$", "");
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