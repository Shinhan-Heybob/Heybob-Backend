package com.shinhan.heybob.chat.domain.communication.service;

import com.shinhan.heybob.chat.domain.communication.dto.PayloadBuilder;
import com.shinhan.heybob.chat.domain.communication.dto.ServerMessage;
import com.shinhan.heybob.chat.domain.communication.util.RetryMechanism;
import com.shinhan.heybob.chat.global.error.ChatException;
import com.shinhan.heybob.chat.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MainServerCommunicationServiceImpl implements MainServerCommunicationService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RetryMechanism retryMechanism;
    
    private static final String CHAT_TO_MAIN_STREAM = "chat-to-main-stream";
    private static final String SERVER_NAME = "CHAT";
    private static final String TARGET_SERVER = "MAIN";
    
    // 응답 대기 중인 메시지들
    private final Map<String, CompletableFuture<ServerMessage>> pendingResponses = new ConcurrentHashMap<>();
    
    @Override
    public void sendMessage(ServerMessage message) {
        try {
            Map<String, Object> streamData = convertToStreamData(message);
            redisTemplate.opsForStream().add(CHAT_TO_MAIN_STREAM, streamData);
            log.info("✅ Main 서버로 메시지 전송: messageType={}, messageId={}", 
                message.getMessageType(), message.getMessageId());
                
        } catch (Exception e) {
            log.error("❌ Main 서버 메시지 전송 실패: messageType={}, messageId={}", 
                message.getMessageType(), message.getMessageId(), e);
            
            // 재시도 가능한 메시지라면 재시도 스케줄링
            if (isRetryableMessage(message.getMessageType())) {
                retryMechanism.scheduleRetry(message, e, () -> sendMessage(message));
            }
            
            throw new ChatException(ErrorCode.COMMUNICATION_FAILED, e);
        }
    }
    
    @Override
    public CompletableFuture<ServerMessage> sendMessageWithResponse(ServerMessage message, long timeoutMs) {
        CompletableFuture<ServerMessage> future = new CompletableFuture<>();
        
        // 응답 대기 등록
        pendingResponses.put(message.getMessageId(), future);
        
        // 타임아웃 설정
        CompletableFuture.delayedExecutor(timeoutMs, TimeUnit.MILLISECONDS).execute(() -> {
            CompletableFuture<ServerMessage> removed = pendingResponses.remove(message.getMessageId());
            if (removed != null && !removed.isDone()) {
                log.error("⏰ Main 서버 응답 타임아웃: messageId={}, messageType={}", 
                    message.getMessageId(), message.getMessageType());
                removed.completeExceptionally(new ChatException(ErrorCode.COMMUNICATION_TIMEOUT));
            }
        });
        
        // 메시지 전송
        sendMessage(message);
        
        return future;
    }
    
    @Override
    public void createRoom(String mealAppointmentId, String creatorUserId, String roomName, List<String> initialMembers) {
        ServerMessage message = ServerMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(ServerMessage.MessageType.CREATE_ROOM)
                .sourceServer(SERVER_NAME)
                .targetServer(TARGET_SERVER)
                .timestamp(LocalDateTime.now())
                .payload(PayloadBuilder.createRoomPayload(mealAppointmentId, creatorUserId, roomName, initialMembers))
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
                
        sendMessage(message);
    }
    
    @Override
    public CompletableFuture<ServerMessage> joinRoom(String roomId, String userId, String userName, String studentId) {
        ServerMessage message = ServerMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(ServerMessage.MessageType.JOIN_ROOM)
                .sourceServer(SERVER_NAME)
                .targetServer(TARGET_SERVER)
                .timestamp(LocalDateTime.now())
                .payload(PayloadBuilder.joinRoomPayload(roomId, userId, userName, studentId))
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(1))
                .build();
                
        return sendMessageWithResponse(message, 30000); // 30초 타임아웃
    }
    
    @Override
    public CompletableFuture<List<Map<String, Object>>> getRoomMembers(String roomId, String requesterId) {
        ServerMessage message = ServerMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(ServerMessage.MessageType.GET_ROOM_MEMBERS)
                .sourceServer(SERVER_NAME)
                .targetServer(TARGET_SERVER)
                .timestamp(LocalDateTime.now())
                .payload(PayloadBuilder.getRoomMembersPayload(roomId, requesterId))
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(1))
                .build();
                
        return sendMessageWithResponse(message, 30000)
                .thenApply(response -> {
                    Map<String, Object> payload = response.getPayload();
                    return (List<Map<String, Object>>) payload.get("members");
                });
    }
    
    @Override
    public CompletableFuture<ServerMessage> processSettlement(String settlementId, String roomId,
                                                             List<String> acceptedUsers, Integer perPersonAmount,
                                                             String note, String requesterId) {
        ServerMessage message = ServerMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(ServerMessage.MessageType.PAYMENT_REQUEST)
                .sourceServer(SERVER_NAME)
                .targetServer(TARGET_SERVER)
                .timestamp(LocalDateTime.now())
                .payload(PayloadBuilder.processSettlementPayload(settlementId, roomId, acceptedUsers, 
                        perPersonAmount, note, requesterId))
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(10)) // 정산은 긴 타임아웃
                .build();
                
        return sendMessageWithResponse(message, 300000); // 5분 타임아웃
    }
    
    @Override
    public CompletableFuture<Boolean> validateUserAccess(String userId, String roomId) {
        ServerMessage message = ServerMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(ServerMessage.MessageType.VALIDATE_USER_ACCESS)
                .sourceServer(SERVER_NAME)
                .targetServer(TARGET_SERVER)
                .timestamp(LocalDateTime.now())
                .payload(PayloadBuilder.validateUserAccessPayload(userId, roomId))
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(1))
                .build();
                
        return sendMessageWithResponse(message, 15000) // 15초 타임아웃
                .thenApply(response -> {
                    Map<String, Object> payload = response.getPayload();
                    return Boolean.TRUE.equals(payload.get("hasAccess"));
                });
    }
    
    @Override
    public void sendSettlementResponse(String settlementId, String userId, String userName, 
                                      String response, String responseTime) {
        ServerMessage message = ServerMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(ServerMessage.MessageType.SETTLEMENT_RESPONSE)
                .sourceServer(SERVER_NAME)
                .targetServer(TARGET_SERVER)
                .timestamp(LocalDateTime.now())
                .payload(PayloadBuilder.settlementResponsePayload(settlementId, userId, userName, response, responseTime))
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
                
        sendMessage(message);
        log.info("📤 정산 응답 Main 서버로 전송: settlementId={}, userId={}, response={}", 
            settlementId, userId, response);
    }
    
    @Override
    public void sendHeartbeat() {
        ServerMessage message = ServerMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(ServerMessage.MessageType.HEARTBEAT)
                .sourceServer(SERVER_NAME)
                .targetServer(TARGET_SERVER)
                .timestamp(LocalDateTime.now())
                .payload(Map.of("status", "alive", "timestamp", System.currentTimeMillis()))
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(1))
                .build();
                
        sendMessage(message);
    }
    
    @Override
    public boolean isMainServerHealthy() {
        // TODO: 헬스체크 로직 구현 (마지막 heartbeat 응답 시간 체크 등)
        return true;
    }
    
    // 응답 메시지 처리 (MainResponseConsumer에서 호출)
    public void handleResponse(ServerMessage response) {
        // correlationId가 없으므로 messageId를 사용
        String messageId = response.getMessageId();
        if (messageId != null) {
            CompletableFuture<ServerMessage> future = pendingResponses.remove(messageId);
            if (future != null) {
                future.complete(response);
                log.debug("✅ 응답 처리 완료: messageId={}", messageId);
            }
        }
    }
    
    private Map<String, Object> convertToStreamData(ServerMessage message) {
        Map<String, Object> streamData = new HashMap<>();
        streamData.put("messageId", message.getMessageId());
        streamData.put("messageType", message.getMessageType().name());
        streamData.put("sourceServer", message.getSourceServer());
        streamData.put("targetServer", message.getTargetServer());
        streamData.put("timestamp", message.getTimestamp().toString());
        streamData.put("retryCount", String.valueOf(message.getRetryCount())); // int를 String으로 변환
        streamData.put("expiryTime", message.getExpiryTime() != null ? message.getExpiryTime().toString() : null);
        
        // Payload를 안전하게 문자열로 변환하여 저장
        if (message.getPayload() != null) {
            message.getPayload().forEach((key, value) -> {
                if (value != null) {
                    streamData.put("payload_" + key, value.toString()); // 모든 값을 String으로 변환
                }
            });
        }
        
        return streamData;
    }
    
    private boolean isRetryableMessage(ServerMessage.MessageType messageType) {
        // 재시도 가능한 메시지 타입 정의
        return switch (messageType) {
            case CREATE_ROOM, GET_ROOM_MEMBERS, VALIDATE_USER_ACCESS -> true;
            case PAYMENT_REQUEST, SAVINGS_REQUEST -> true;  // 새 Payment 관련 타입들
            case JOIN_ROOM -> false; // 중복 입장 방지
            case HEARTBEAT -> false; // 즉시성이 중요
            default -> true;
        };
    }
}