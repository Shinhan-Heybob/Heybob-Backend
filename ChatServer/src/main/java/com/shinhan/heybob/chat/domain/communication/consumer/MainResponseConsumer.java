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
                case USER_ACCESS_RESPONSE:
                case SETTLEMENT_PROCESSED:
                    // 응답 메시지는 대기 중인 Future에 전달
                    communicationService.handleResponse(message);
                    break;
                    
                // 4가지 주요 금융 메시지 타입
                case PAYMENT_REQUEST:
                case SAVINGS_REQUEST:
                case PAYMENT_COMPLETE:
                case SAVINGS_COMPLETE:
                    // 모든 금융 관련 메시지는 통합 핸들러로 처리
                    messageHandler.handleMessage(message);
                    break;
                    
                // 기타 알림 메시지
                case ROOM_STATUS_CHANGED:
                case MEMBER_JOINED:
                case MEMBER_LEFT:
                    messageHandler.handleMessage(message);
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
            
            if (streamData == null || streamData.isEmpty()) {
                throw new IllegalArgumentException("Stream 데이터가 null이거나 비어있습니다");
            }
            
            // Stream 데이터를 ServerMessage 객체로 변환
            Map<String, Object> payload = new HashMap<>();
            
            // payload_ 접두사가 붙은 필드들을 추출
            for (Map.Entry<Object, Object> entry : streamData.entrySet()) {
                try {
                    String key = entry.getKey().toString();
                    if (key.startsWith("payload_")) {
                        String payloadKey = key.substring("payload_".length());
                        Object value = entry.getValue();
                        
                        // Payload 값도 문자열인 경우 따옴표 제거
                        if (value instanceof String) {
                            value = ((String) value).replaceAll("^\"|\"$", "");
                        }
                        
                        payload.put(payloadKey, value);
                        log.debug("📝 Payload 추출: {} = {}", payloadKey, value);
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Payload 항목 처리 실패: key={}, value={}, error={}", 
                        entry.getKey(), entry.getValue(), e.getMessage());
                }
            }
            
            // 필수 필드들을 안전하게 추출
            String messageId = getString(streamData, "messageId");
            log.debug("🆔 messageId: {}", messageId);
            
            String messageTypeStr = getString(streamData, "messageType");
            log.info("🔍 messageType 원본 값: [{}], 길이: {}, 타입: {}", 
                messageTypeStr, 
                messageTypeStr != null ? messageTypeStr.length() : "null",
                messageTypeStr != null ? messageTypeStr.getClass().getSimpleName() : "null");
            
            // Raw 값도 확인
            Object rawMessageType = streamData.get("messageType");
            log.info("🔍 messageType Raw 값: [{}], 타입: {}", 
                rawMessageType,
                rawMessageType != null ? rawMessageType.getClass().getSimpleName() : "null");
            
            String sourceServer = getString(streamData, "sourceServer");
            log.debug("📤 sourceServer: {}", sourceServer);
            
            String targetServer = getString(streamData, "targetServer");
            log.debug("📥 targetServer: {}", targetServer);
            
            String timestampStr = getString(streamData, "timestamp");
            log.debug("⏰ timestamp: {}", timestampStr);
            
            log.info("📋 추출된 필드들: messageId={}, messageType={}, sourceServer={}, targetServer={}, timestamp={}, payload={}", 
                messageId, messageTypeStr, sourceServer, targetServer, timestampStr, payload);
            
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
            
            // MessageType enum 안전하게 변환
            ServerMessage.MessageType messageType;
            try {
                if (messageTypeStr == null) {
                    throw new IllegalArgumentException("messageTypeStr is null");
                }
                messageType = ServerMessage.MessageType.valueOf(messageTypeStr.trim());
                log.debug("✅ MessageType 변환 성공: {}", messageType);
            } catch (IllegalArgumentException e) {
                log.error("❌❌❌ CRITICAL ERROR: 알 수 없는 MessageType");
                log.error("🔍 입력 값: '{}'", messageTypeStr);
                log.error("🔢 문자열 길이: {}", messageTypeStr != null ? messageTypeStr.length() : "null");
                
                // 지원되는 모든 MessageType 출력
                log.error("✅ 지원하는 MessageType 목록:");
                for (ServerMessage.MessageType type : ServerMessage.MessageType.values()) {
                    log.error("   - {}", type.name());
                }
                
                // 디버깅을 위해 각 문자의 아스키 코드도 출력
                if (messageTypeStr != null) {
                    StringBuilder hexDump = new StringBuilder();
                    for (char c : messageTypeStr.toCharArray()) {
                        hexDump.append(String.format("[%c:%d] ", c, (int)c));
                    }
                    log.error("🔤 입력 문자 상세 분석: {}", hexDump.toString());
                }
                
                // 유사한 타입 찾기 시도
                if (messageTypeStr != null) {
                    String cleanType = messageTypeStr.trim().toUpperCase();
                    log.error("🧹 정리된 입력: '{}'", cleanType);
                    
                    for (ServerMessage.MessageType type : ServerMessage.MessageType.values()) {
                        if (type.name().equals(cleanType)) {
                            log.error("💡 제안: 공백이나 대소문자 문제일 수 있습니다. '{}' 사용을 권장합니다.", type.name());
                        }
                    }
                }
                
                throw new RuntimeException("❌ FATAL: 지원하지 않는 MessageType: '" + messageTypeStr + "'. 위의 지원 목록을 확인하세요.", e);
            }

            // expiryTime 안전하게 파싱
            LocalDateTime expiryTime = null;
            String expiryTimeStr = getString(streamData, "expiryTime");
            if (expiryTimeStr != null && !expiryTimeStr.isEmpty()) {
                try {
                    expiryTime = LocalDateTime.parse(expiryTimeStr);
                    log.debug("✅ expiryTime 파싱 성공: {}", expiryTime);
                } catch (Exception e) {
                    log.warn("⚠️ expiryTime 파싱 실패: {} -> null로 설정", expiryTimeStr, e);
                }
            }

            ServerMessage result = ServerMessage.builder()
                    .messageId(messageId)
                    .messageType(messageType)
                    .sourceServer(sourceServer)
                    .targetServer(targetServer)
                    .timestamp(timestamp)
                    .payload(payload)
                    .retryCount(getInteger(streamData, "retryCount"))
                    .expiryTime(expiryTime)
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
            String mealAppointmentId = (String) payload.get("mealAppointmentId");
            String creatorUserId = (String) payload.get("creatorUserId");
            String roomName = (String) payload.get("roomName");
            List<String> initialMembers = (List<String>) payload.get("initialMembers");
            
            log.info("📢 채팅방 생성 요청 수신: mealAppointmentId={}, creator={}, roomName={}", 
                mealAppointmentId, creatorUserId, roomName);
            
            // 채팅방 ID 생성 (실제로는 DB에 저장하고 ID를 받아야 함)
            Long chatRoomId = System.currentTimeMillis() % 1000000;
            
            // 응답 메시지 생성
            ServerMessage response = ServerMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .messageType(ServerMessage.MessageType.ROOM_CREATED)
                .sourceServer("CHAT")
                .targetServer("MAIN")
                .timestamp(LocalDateTime.now())
                .payload(Map.of(
                    "chatRoomId", chatRoomId,
                    "mealAppointmentId", mealAppointmentId,
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
        data.put("messageType", message.getMessageType().name());
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