package com.shinhan.heybob.domain.meal.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.dto.ServerMessage;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.entity.MealParticipant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatIntegrationServiceImpl implements ChatIntegrationService {
    
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
    public Long createChatRoom(MealAppointment mealAppointment) {
        try {
            String messageId = UUID.randomUUID().toString();
            
            // 참여자 ID 목록 생성
            List<String> participantIds = mealAppointment.getParticipants().stream()
                .map(participant -> participant.getUser().getId().toString())
                .collect(Collectors.toList());
            
            // 채팅방 생성 메시지 준비
            Map<String, Object> payload = new HashMap<>();
            payload.put("bob약Id", mealAppointment.getId().toString());
            payload.put("creatorUserId", mealAppointment.getCreator().getId().toString());
            payload.put("roomName", mealAppointment.getName());
            payload.put("initialMembers", participantIds);
            
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
            
            // Redis Stream으로 메시지 전송
            Map<String, Object> streamData = convertToStreamData(message);
            redisTemplate.opsForStream().add(MAIN_TO_CHAT_STREAM, streamData);
            
            log.info("✅ 채팅방 생성 요청 전송: 밥약ID={}, messageId={}", mealAppointment.getId(), messageId);
            
            // 응답 대기
            CompletableFuture<ServerMessage> future = new CompletableFuture<>();
            pendingResponses.put(messageId, future);
            
            // 타임아웃 설정
            CompletableFuture.delayedExecutor(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS).execute(() -> {
                CompletableFuture<ServerMessage> removed = pendingResponses.remove(messageId);
                if (removed != null && !removed.isDone()) {
                    log.error("⏰ 채팅방 생성 응답 타임아웃: messageId={}", messageId);
                    removed.completeExceptionally(new HeybobException(ExceptionStatus.CHAT_INTEGRATION_FAILED));
                }
            });
            
            // 응답 대기 및 채팅방 ID 추출
            ServerMessage response = future.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            if (response.getPayload() != null && response.getPayload().containsKey("chatRoomId")) {
                Long chatRoomId = Long.valueOf(response.getPayload().get("chatRoomId").toString());
                log.info("✅ 채팅방 생성 완료: 밥약ID={}, 채팅방ID={}", mealAppointment.getId(), chatRoomId);
                return chatRoomId;
            } else {
                log.error("❌ 채팅방 생성 응답에 chatRoomId가 없음: response={}", response);
                throw new HeybobException(ExceptionStatus.CHAT_INTEGRATION_FAILED);
            }
            
        } catch (Exception e) {
            log.error("❌ 채팅방 생성 실패: 밥약ID={}", mealAppointment.getId(), e);
            // Fallback: 더미 채팅방 ID 반환
            Long fallbackRoomId = System.currentTimeMillis() % 1000000;
            log.warn("⚠️ Fallback 채팅방 ID 사용: {}", fallbackRoomId);
            return fallbackRoomId;
        }
    }
    
    private Map<String, Object> convertToStreamData(ServerMessage message) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", message.getMessageId());
        data.put("messageType", message.getMessageType().toString());
        data.put("sourceServer", message.getSourceServer());
        data.put("targetServer", message.getTargetServer());
        data.put("timestamp", message.getTimestamp().toString());
        data.put("retryCount", message.getRetryCount());
        data.put("expiryTime", message.getExpiryTime().toString());
        
        // payload 데이터를 payload_ 접두사와 함께 추가 (Chat Server 호환성)
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
    
    @Override
    public String sendSettlementBroadcast(String settlementId, String roomId, String requesterName, 
                                        Integer requestAmount, String message) {
        try {
            String messageId = UUID.randomUUID().toString();
            
            // 정산 브로드캐스트 메시지 생성
            Map<String, Object> payload = new HashMap<>();
            payload.put("settlementId", settlementId);
            payload.put("roomId", roomId);
            payload.put("requesterName", requesterName);
            payload.put("requestAmount", requestAmount);
            payload.put("message", message);
            
            ServerMessage serverMessage = ServerMessage.builder()
                .messageId(messageId)
                .messageType(ServerMessage.MessageType.BROADCAST_SETTLEMENT_REQUEST)
                .sourceServer(SERVER_NAME)
                .targetServer(TARGET_SERVER)
                .timestamp(LocalDateTime.now())
                .payload(payload)
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
            
            // Redis Stream으로 메시지 전송
            Map<String, Object> streamData = convertToStreamData(serverMessage);
            redisTemplate.opsForStream().add(MAIN_TO_CHAT_STREAM, streamData);
            
            log.info("✅ 정산 브로드캐스트 전송 완료: messageId={}, settlementId={}, roomId={}", 
                messageId, settlementId, roomId);
            
            return messageId;
            
        } catch (Exception e) {
            log.error("❌ 정산 브로드캐스트 전송 실패: settlementId={}, roomId={}", settlementId, roomId, e);
            throw new RuntimeException("정산 브로드캐스트 전송 실패: " + e.getMessage());
        }
    }
    
    private ServerMessage convertFromStreamData(Map<Object, Object> data) {
        return ServerMessage.builder()
            .messageId((String) data.get("messageId"))
            .messageType(ServerMessage.MessageType.valueOf((String) data.get("messageType")))
            .sourceServer((String) data.get("sourceServer"))
            .targetServer((String) data.get("targetServer"))
            .timestamp(LocalDateTime.parse((String) data.get("timestamp")))
            .payload((Map<String, Object>) data.get("payload"))
            .retryCount((Integer) data.getOrDefault("retryCount", 0))
            .expiryTime(LocalDateTime.parse((String) data.get("expiryTime")))
            .build();
    }
}