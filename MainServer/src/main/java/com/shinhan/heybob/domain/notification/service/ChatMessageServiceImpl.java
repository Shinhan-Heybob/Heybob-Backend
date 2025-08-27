package com.shinhan.heybob.domain.notification.service;

import com.shinhan.heybob.domain.notification.dto.ChatBroadcastRequest;
import com.shinhan.heybob.domain.notification.dto.ServerMessage;
import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {
    
    private final RedisTemplate<String, String> streamRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String MAIN_TO_CHAT_STREAM = "main-to-chat-stream";
    private static final String CHAT_TO_MAIN_STREAM = "chat-to-main-stream";
    private static final String SERVER_NAME = "MAIN";
    private static final String TARGET_SERVER = "CHAT";
    private static final long DEFAULT_TIMEOUT_MS = 5000;
    
    private final Map<String, CompletableFuture<ServerMessage>> pendingResponses = new ConcurrentHashMap<>();
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
    private Subscription subscription;
    
    @PostConstruct
    public void initialize() {
        try {
            // Redis Stream 리스너 설정
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                    .builder()
                    .pollTimeout(Duration.ofSeconds(1))
                    .build();
            
            listenerContainer = StreamMessageListenerContainer.create(redisTemplate.getConnectionFactory(), options);
            
            // 채팅 서버로부터의 응답 처리
            subscription = listenerContainer.receive(
                StreamOffset.create(CHAT_TO_MAIN_STREAM, ReadOffset.lastConsumed()),
                (MapRecord<String, String, String> message) -> {
                    Map<Object, Object> data = new HashMap<>();
                    message.getValue().forEach(data::put);
                    handleChatServerResponse(data);
                }
            );
            
            listenerContainer.start();
            log.info("✅ Redis Stream 리스너 초기화 완료");
            
        } catch (Exception e) {
            log.error("❌ Redis Stream 리스너 초기화 실패", e);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        if (subscription != null) {
            listenerContainer.remove(subscription);
        }
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }
    
    @Override
    public String sendMessage(ServerMessage message) {
        try {
            // Redis Stream으로 메시지 전송
            Map<String, Object> streamData = convertToStreamData(message);
            redisTemplate.opsForStream().add(MAIN_TO_CHAT_STREAM, streamData);
            
            log.info("✅ 메시지 전송 완료: messageId={}, type={}", 
                message.getMessageId(), message.getMessageType());
            
            return message.getMessageId();
            
        } catch (Exception e) {
            log.error("❌ 메시지 전송 실패: messageId={}", message.getMessageId(), e);
            throw new RuntimeException("메시지 전송 실패: " + e.getMessage());
        }
    }
    
    @Override
    public CompletableFuture<ServerMessage> sendMessageWithResponse(ServerMessage message, long timeoutMs) {
        try {
            // 메시지 전송
            sendMessage(message);
            
            // 응답 대기 Future 생성
            CompletableFuture<ServerMessage> future = new CompletableFuture<>();
            pendingResponses.put(message.getMessageId(), future);
            
            // 타임아웃 설정
            CompletableFuture.delayedExecutor(timeoutMs, TimeUnit.MILLISECONDS).execute(() -> {
                CompletableFuture<ServerMessage> removed = pendingResponses.remove(message.getMessageId());
                if (removed != null && !removed.isDone()) {
                    log.error("⏰ 응답 타임아웃: messageId={}", message.getMessageId());
                    removed.completeExceptionally(new HeybobException(ExceptionStatus.CHAT_INTEGRATION_FAILED));
                }
            });
            
            return future;
            
        } catch (Exception e) {
            log.error("❌ 메시지 전송 실패: messageId={}", message.getMessageId(), e);
            CompletableFuture<ServerMessage> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    // ✅ 정산 시작(요청) 브로드캐스트: PAYMENT_REQUEST
    public String sendSettlementStart(
            Long settlementId,
            Long roomId,
            Long requesterId,
            String requesterName,
            String requesterStudentId,
            String requesterProfileImg,
            int perHeadAmount,
            String message
    ) {
        Map<String, String> m = baseHeaders(ServerMessage.MessageType.PAYMENT_REQUEST);
        m.put("payload_settlementId", str(settlementId));
        m.put("payload_roomId", str(roomId));
        m.put("payload_requesterId", str(requesterId));
        m.put("payload_requesterName", str(requesterName));
        m.put("payload_requesterStudentId", str(requesterStudentId));
        m.put("payload_requesterProfileImg", str(requesterProfileImg));
        m.put("payload_requestAmount", str(perHeadAmount));
        m.put("payload_message", str(message));

        streamRedisTemplate.opsForStream().add(MAIN_TO_CHAT_STREAM, m);
        log.info("PAYMENT_REQUEST published: {}", m);
        return m.get("messageId");
    }

    // ✅ 개별 결제 완료 브로드캐스트: PAYMENT_COMPLETE
    public String sendPaymentComplete(
            Long settlementId,
            Long roomId,
            Long recipientId,     // 받는 사람(정산 개시자)
            String recipientName,
            int completedAmount,
            Long payerId,         // (선택) 보낸 사람
            String payerName
    ) {
        Map<String, String> m = baseHeaders(ServerMessage.MessageType.PAYMENT_COMPLETE);
        m.put("payload_settlementId", str(settlementId));
        m.put("payload_roomId", str(roomId));
        m.put("payload_recipientId", str(recipientId));
        m.put("payload_recipientName", str(recipientName));
        m.put("payload_completedAmount", str(completedAmount));
        if (payerId != null)   m.put("payload_payerId", str(payerId));
        if (payerName != null) m.put("payload_payerName", str(payerName));

        streamRedisTemplate.opsForStream().add(MAIN_TO_CHAT_STREAM, m);
        log.info("PAYMENT_COMPLETE published: {}", m);
        return m.get("messageId");
    }
    
    @Override
    public CompletableFuture<Long> createChatRoom(String roomName, String creatorUserId, 
                                                  List<String> initialMembers, 
                                                  Map<String, Object> metadata) {
        try {
            String messageId = UUID.randomUUID().toString();
            
            // 채팅방 생성 메시지 준비
            Map<String, Object> payload = new HashMap<>();
            payload.put("creatorUserId", creatorUserId);
            payload.put("roomName", roomName);
            payload.put("initialMembers", initialMembers);
            if (metadata != null) {
                payload.putAll(metadata);
            }
            
            ServerMessage message = ServerMessage.builder()
                .messageId(messageId)
                .messageType(ServerMessage.MessageType.CREATE_ROOM)
                .sourceServer(SERVER_NAME)
                .targetServer(TARGET_SERVER)
                .timestamp(LocalDateTime.now())
                .payload(payload)
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
            
            // 응답 대기와 함께 메시지 전송
            CompletableFuture<ServerMessage> responseFuture = sendMessageWithResponse(message, DEFAULT_TIMEOUT_MS);
            
            // 채팅방 ID 추출
            return responseFuture.thenApply(response -> {
                if (response.getPayload() != null && response.getPayload().containsKey("chatRoomId")) {
                    Long chatRoomId = Long.valueOf(response.getPayload().get("chatRoomId").toString());
                    log.info("✅ 채팅방 생성 완료: chatRoomId={}", chatRoomId);
                    return chatRoomId;
                } else {
                    log.error("❌ 채팅방 생성 응답에 chatRoomId가 없음: response={}", response);
                    throw new HeybobException(ExceptionStatus.CHAT_INTEGRATION_FAILED);
                }
            }).exceptionally(ex -> {
                log.error("❌ 채팅방 생성 실패", ex);
                // Fallback: 더미 채팅방 ID 반환
                Long fallbackRoomId = System.currentTimeMillis() % 1000000;
                log.warn("⚠️ Fallback 채팅방 ID 사용: {}", fallbackRoomId);
                return fallbackRoomId;
            });
            
        } catch (Exception e) {
            log.error("❌ 채팅방 생성 요청 실패", e);
            CompletableFuture<Long> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
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
    
    private void handleChatServerResponse(Map<Object, Object> data) {
        try {
            String messageId = (String) data.get("correlationId");
            if (messageId == null) {
                messageId = (String) data.get("messageId");
            }
            
            if (messageId != null) {
                CompletableFuture<ServerMessage> future = pendingResponses.remove(messageId);
                if (future != null) {
                    ServerMessage response = convertFromStreamData(data);
                    future.complete(response);
                    log.info("✅ 채팅 서버 응답 처리: messageId={}, type={}", 
                        messageId, data.get("messageType"));
                }
            }
        } catch (Exception e) {
            log.error("❌ 채팅 서버 응답 처리 실패", e);
        }
    }
    
    private ServerMessage convertFromStreamData(Map<Object, Object> data) {
        // payload 데이터 추출
        Map<String, Object> payload = new HashMap<>();
        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            String key = entry.getKey().toString();
            if (key.startsWith("payload_")) {
                payload.put(key.substring(8), entry.getValue());
            }
        }
        
        return ServerMessage.builder()
            .messageId((String) data.get("messageId"))
            .messageType(ServerMessage.MessageType.valueOf((String) data.get("messageType")))
            .sourceServer((String) data.get("sourceServer"))
            .targetServer((String) data.get("targetServer"))
            .timestamp(LocalDateTime.parse((String) data.get("timestamp")))
            .payload(payload)
            .retryCount(Integer.parseInt(data.getOrDefault("retryCount", "0").toString()))
            .expiryTime(LocalDateTime.parse((String) data.get("expiryTime")))
            .build();
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private Map<String, String> baseHeaders(ServerMessage.MessageType type) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("messageId", java.util.UUID.randomUUID().toString());
        m.put("messageType", type.name());
        m.put("sourceServer", SERVER_NAME);
        m.put("targetServer", TARGET_SERVER);
        m.put("timestamp", java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).toString());
        m.put("retryCount", "0");
        // expiryTime은 컨슈머에서 필수 아님 → 필요시 m.put("expiryTime", ...)
        return m;
    }

}