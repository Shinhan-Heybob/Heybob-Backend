package com.shinhan.heybob.chat.domain.communication.handler;

import com.shinhan.heybob.chat.domain.communication.dto.ServerMessage;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.dto.PaymentRequestData;
import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import com.shinhan.heybob.chat.domain.chat.dto.UiState;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageHandler {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;
    
    
    public void handleNotification(ServerMessage message) {
        try {
            switch (message.getMessageType()) {
                case ROOM_STATUS_CHANGED:
                    handleRoomStatusChanged(message);
                    break;
                    
                case MEMBER_JOINED:
                    handleMemberJoined(message);
                    break;
                    
                case MEMBER_LEFT:
                    handleMemberLeft(message);
                    break;
                    
                case PAYMENT_COMPLETED:
                    handleSettlementCompleted(message);
                    break;
                    
                default:
                    log.warn("⚠️ 처리되지 않은 알림 타입: {}", message.getMessageType());
            }
        } catch (Exception e) {
            log.error("❌ 알림 메시지 처리 실패: messageType={}, messageId={}", 
                message.getMessageType(), message.getMessageId(), e);
        }
    }
    
    private void handleRoomStatusChanged(ServerMessage message) {
        Map<String, Object> payload = message.getPayload();
        String roomId = (String) payload.get("roomId");
        String newStatus = (String) payload.get("newStatus");
        String reason = (String) payload.get("reason");
        
        log.info("🔄 채팅방 상태 변경: roomId={}, status={}, reason={}", roomId, newStatus, reason);
        
        // 해당 방의 모든 사용자에게 브로드캐스트
        Map<String, Object> notification = Map.of(
            "type", "ROOM_STATUS_CHANGED",
            "roomId", roomId,
            "newStatus", newStatus,
            "reason", reason,
            "timestamp", message.getTimestamp()
        );
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/status", notification);
    }
    
    private void handleMemberJoined(ServerMessage message) {
        Map<String, Object> payload = message.getPayload();
        String roomId = (String) payload.get("roomId");
        String userId = (String) payload.get("userId");
        String userName = (String) payload.get("userName");
        
        log.info("👋 새 멤버 입장: roomId={}, userId={}, userName={}", roomId, userId, userName);
        
        // 해당 방의 모든 사용자에게 브로드캐스트 (입장한 사용자 제외)
        Map<String, Object> notification = Map.of(
            "type", "MEMBER_JOINED",
            "roomId", roomId,
            "userId", userId,
            "userName", userName,
            "timestamp", message.getTimestamp()
        );
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/members", notification);
    }
    
    private void handleMemberLeft(ServerMessage message) {
        Map<String, Object> payload = message.getPayload();
        String roomId = (String) payload.get("roomId");
        String userId = (String) payload.get("userId");
        String userName = (String) payload.get("userName");
        String reason = (String) payload.get("reason");
        
        log.info("👋 멤버 퇴장: roomId={}, userId={}, userName={}, reason={}", 
            roomId, userId, userName, reason);
        
        Map<String, Object> notification = Map.of(
            "type", "MEMBER_LEFT",
            "roomId", roomId,
            "userId", userId,
            "userName", userName,
            "reason", reason,
            "timestamp", message.getTimestamp()
        );
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/members", notification);
    }
    
    private void handleSettlementCompleted(ServerMessage message) {
        Map<String, Object> payload = message.getPayload();
        String roomId = (String) payload.get("roomId");
        String settlementId = (String) payload.get("settlementId");
        String status = (String) payload.get("status");
        List<Map<String, Object>> paymentResults = (List<Map<String, Object>>) payload.get("paymentResults");
        String totalAmount = (String) payload.get("totalAmount");
        String completionMessage = (String) payload.get("message");
        
        log.info("💰 정산 완료 알림: roomId={}, settlementId={}, status={}", 
            roomId, settlementId, status);
        
        // 정산 완료 메시지를 해당 방에 브로드캐스트
        Map<String, Object> notification = Map.of(
            "type", "SETTLEMENT_COMPLETED",
            "roomId", roomId,
            "settlementId", settlementId,
            "status", status,
            "paymentResults", paymentResults,
            "totalAmount", totalAmount,
            "message", completionMessage,
            "timestamp", message.getTimestamp()
        );
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/settlement", notification);
        
        // 개별 사용자에게도 결과 전송 (성공/실패)
        for (Map<String, Object> result : paymentResults) {
            String userId = (String) result.get("userId");
            String userStatus = (String) result.get("status");
            String userMessage = (String) result.get("message");
            
            Map<String, Object> userNotification = Map.of(
                "type", "PERSONAL_SETTLEMENT_RESULT",
                "settlementId", settlementId,
                "status", userStatus,
                "message", userMessage,
                "timestamp", message.getTimestamp()
            );
            
            messagingTemplate.convertAndSendToUser(userId, "/queue/settlement", userNotification);
        }
    }
    
    /**
     * Main 서버의 정산 브로드캐스트 요청 처리 (단순화)
     */
    public void handleSettlementBroadcast(ServerMessage message) {
        try {
            log.info("🔍 정산 브로드캐스트 처리 시작: messageId={}", message.getMessageId());
            
            Map<String, Object> payload = message.getPayload();
            log.info("🔍 Payload 내용: {}", payload);
            
            String settlementId = (String) payload.get("settlementId");
            String roomId = (String) payload.get("roomId");
            String requesterName = (String) payload.get("requesterName");
            
            // requestAmount 안전한 변환 (Redis Stream에서는 String으로 전달됨)
            Integer requestAmount = getIntegerFromPayload(payload, "requestAmount", 15000);
            
            log.info("🔍 추출된 데이터: settlementId={}, roomId={}, requester={}, amount={}", 
                settlementId, roomId, requesterName, requestAmount);
            
            log.info("💰 Main 서버로부터 정산 브로드캐스트 요청: settlementId={}, roomId={}, requester={}", 
                settlementId, roomId, requesterName);
            
            // 단순한 정산 데이터 생성
            SettlementData settlementData = SettlementData.builder()
                .settlementId(settlementId)
                .roomId(roomId)
                .requesterName(requesterName)
                .requestAmount(requestAmount)
                .settlementUrl("/main/settlement/" + settlementId)
                .build();
            
            log.info("🔍 SettlementData 생성 완료: {}", settlementData);
            
            // 정산 메시지 생성 및 방에 브로드캐스트
            log.info("🔍 broadcastSettlementMessage 호출 시작");
            broadcastSettlementMessage(roomId, settlementData);
            log.info("🔍 broadcastSettlementMessage 호출 완료");
            
        } catch (Exception e) {
            log.error("❌ 정산 브로드캐스트 처리 실패: messageId={}", message.getMessageId(), e);
        }
    }
    
    private void broadcastSettlementMessage(String roomId, SettlementData settlementData) {
        try {
            String messageId = UUID.randomUUID().toString();
            
            // 1. MongoDB에 정산 메시지 저장
            ChatMessage chatMessage = ChatMessage.builder()
                .id(messageId)  // MongoDB _id로 사용
                .roomId(roomId)
                .senderId("system")
                .studentId("SYSTEM")
                .senderName("시스템")
                .profileImageUrl(null)
                .content(String.format("%s님이 이체하기를 요청했습니다!", settlementData.getRequesterName()))
                .messageType(ChatMessage.MessageType.PAYMENT_REQUEST)
                .timestamp(LocalDateTime.now())
                .paymentRequestData(com.shinhan.heybob.chat.domain.chat.dto.PaymentRequestData.builder()
                    .settlementId(settlementData.getSettlementId())
                    .roomId(settlementData.getRoomId())
                    .requesterName(settlementData.getRequesterName())
                    .requestAmount(settlementData.getRequestAmount())
                    .settlementUrl(settlementData.getSettlementUrl())
                    .build())
                .paymentCompleteData(null)  // 정산 요청이므로 null
                .emergencyFallback(false)  // 정상적인 Redis Stream 처리
                .build();
            
            // MongoDB에 저장
            try {
                chatService.saveMessage(chatMessage);
                log.info("💾 정산 메시지 MongoDB 저장 완료: messageId={}", messageId);
            } catch (Exception saveException) {
                log.error("❌ 정산 메시지 MongoDB 저장 실패: messageId={}", messageId, saveException);
            }
            
            // 2. WebSocket으로 실시간 브로드캐스트
            ChatMessageResponse settlementMessage = ChatMessageResponse.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId("system")
                .senderName("시스템")
                .content(chatMessage.getContent())
                .messageType("PAYMENT_REQUEST")
                .timestamp(chatMessage.getTimestamp())
                .paymentRequestData(PaymentRequestData.builder()
                    .settlementId(settlementData.getSettlementId())
                    .roomId(settlementData.getRoomId())
                    .requesterName(settlementData.getRequesterName())
                    .requestAmount(settlementData.getRequestAmount())
                    .settlementUrl(settlementData.getSettlementUrl())
                    .build())
                .uiState(UiState.builder()
                    .isRequester(false)
                    .userResponseStatus("unknown")
                    .availableActions(Arrays.asList("go_to_settlement"))
                    .isExpired(false)
                    .build())
                .build();
            
            // 해당 방의 모든 사용자에게 브로드캐스트
            messagingTemplate.convertAndSend("/topic/room/" + roomId, settlementMessage);
            
            log.info("📨 정산 메시지 브로드캐스트 완료: roomId={}, settlementId={}, messageId={}", 
                roomId, settlementData.getSettlementId(), messageId);
                
        } catch (Exception e) {
            log.error("❌ 정산 메시지 저장/전송 실패: roomId={}, settlementId={}", 
                roomId, settlementData.getSettlementId(), e);
        }
    }
    
    /**
     * Payload에서 Integer 값을 안전하게 추출
     */
    private Integer getIntegerFromPayload(Map<String, Object> payload, String key, Integer defaultValue) {
        Object value = payload.get(key);
        if (value == null) return defaultValue;
        
        try {
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            } else {
                return Integer.parseInt(value.toString());
            }
        } catch (NumberFormatException e) {
            log.warn("⚠️ {} 변환 실패, 기본값 사용: {} -> {}", key, value, defaultValue);
            return defaultValue;
        }
    }
}