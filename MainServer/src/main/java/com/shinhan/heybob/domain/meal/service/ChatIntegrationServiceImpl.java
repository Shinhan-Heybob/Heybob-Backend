package com.shinhan.heybob.domain.meal.service;

import com.shinhan.heybob.domain.notification.dto.ChatBroadcastRequest;
import com.shinhan.heybob.domain.notification.dto.ServerMessage;
import com.shinhan.heybob.domain.notification.service.ChatMessageService;
import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatIntegrationServiceImpl implements ChatIntegrationService {
    
    private final ChatMessageService chatMessageService;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String MAIN_TO_CHAT_STREAM = "main-to-chat-stream";
    
    @Override
    public Long createChatRoom(MealAppointment mealAppointment) {
        try {
            // 참여자 ID 목록 생성
            List<String> participantIds = mealAppointment.getParticipants().stream()
                .map(participant -> participant.getUser().getId().toString())
                .collect(Collectors.toList());
            
            // 메타데이터 준비
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("mealAppointmentId", mealAppointment.getId().toString());
            
            // 공통 서비스를 통해 채팅방 생성
            CompletableFuture<Long> chatRoomFuture = chatMessageService.createChatRoom(
                mealAppointment.getName(),
                mealAppointment.getCreator().getId().toString(),
                participantIds,
                metadata
            );
            
            // 응답 대기 (타임아웃 5초)
            Long chatRoomId = chatRoomFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
            log.info("✅ 채팅방 생성 완료: 밥약ID={}, 채팅방ID={}", mealAppointment.getId(), chatRoomId);
            return chatRoomId;
            
        } catch (Exception e) {
            log.error("❌ 채팅방 생성 실패: 밥약ID={}", mealAppointment.getId(), e);
            // Fallback: 더미 채팅방 ID 반환
            Long fallbackRoomId = System.currentTimeMillis() % 1000000;
            log.warn("⚠️ Fallback 채팅방 ID 사용: {}", fallbackRoomId);
            return fallbackRoomId;
        }
    }
    
    
    @Override
    public String sendSettlementBroadcast(Long settlementId, Long chatRoomId, Long requesterId, Integer requestAmount
    ) {
        try {
            User initiator = userRepository.findById(requesterId)
                    .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

            String settlementIdStr = String.valueOf(settlementId);
            String chatRoomIdStr = String.valueOf(chatRoomId);
            String requesterName = initiator.getName();
            String requesterStudentId = initiator.getStudentId();
            String requesterProfileImg = initiator.getProfileUrl();

            ChatBroadcastRequest request = ChatBroadcastRequest.builder()
                .settlementId(settlementIdStr)
                .roomId(chatRoomIdStr)
                .requesterId(requesterId)  // 더미 ID
                .requesterName(requesterName)
                .requesterStudentId(requesterStudentId)  // 더미 학번
                .requesterProfileImg(requesterProfileImg) // 더미 프로필
                .requestAmount(requestAmount)
                .message("")
                .type(ChatBroadcastRequest.BroadcastType.PAYMENT)
                .build();
            
            // 공통 서비스를 통해 정산 브로드캐스트 전송
            String messageId = chatMessageService.sendSettlementBroadcast(request);
            
            log.info("✅ 정산 브로드캐스트 전송 완료: messageId={}, settlementId={}, roomId={}", 
                messageId, settlementId, chatRoomId);
            
            return messageId;
            
        } catch (Exception e) {
            log.error("❌ 정산 브로드캐스트 전송 실패: settlementId={}, roomId={}", settlementId, chatRoomId, e);
            throw new RuntimeException("정산 브로드캐스트 전송 실패: " + e.getMessage());
        }
    }

    @Override
    public String sendSettlementPaymentComplete(Long settlementId, Long chatRoomId, Long recipientId,
                                                String recipientName, int completedAmount
    ) {
        try {
            String messageId = UUID.randomUUID().toString();

            // 요청 페이로드 구성
            Map<String, Object> paymentComplete = new HashMap<>();
            paymentComplete.put("settlementId", String.valueOf(settlementId));
            paymentComplete.put("roomId",       String.valueOf(chatRoomId));
            paymentComplete.put("recipientId",  String.valueOf(recipientId));
            paymentComplete.put("recipientName", recipientName);
            paymentComplete.put("completedAmount", completedAmount);

            Map<String, Object> payload = new HashMap<>();
            payload.put("paymentRequestData", null);
            payload.put("paymentCompleteData", paymentComplete);

            ServerMessage message = ServerMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .messageType(ServerMessage.MessageType.PAYMENT_COMPLETE) // enum에 추가
                    .sourceServer("MAIN")
                    .targetServer("CHAT")
                    .timestamp(LocalDateTime.now())
                    .payload(payload)
                    .retryCount(0)
                    .expiryTime(LocalDateTime.now().plusMinutes(5))
                    .build();

            Map<String, Object> streamData = convertToStreamData(message);
            log.info("🔍 (COMPLETE) Redis Stream 전송 데이터: {}", streamData);

            redisTemplate.opsForStream().add(MAIN_TO_CHAT_STREAM, streamData);

            log.info("✅ 정산 완료 브로드캐스트 전송 완료: messageId={}, settlementId={}",
                    messageId, settlementId);

            return messageId;
        } catch (Exception e) {}

        return "";
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
}