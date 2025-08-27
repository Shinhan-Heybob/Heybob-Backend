package com.shinhan.heybob.chat.domain.communication.handler;

import com.shinhan.heybob.chat.domain.communication.dto.ServerMessage;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.dto.PaymentRequestData;
import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
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
    private final FinancialMessageService financialMessageService;
    
    
    /**
     * 메시지 타입별 라우팅 처리
     * 4가지 주요 타입: PAYMENT_REQUEST, SAVINGS_REQUEST, PAYMENT_COMPLETE, SAVINGS_COMPLETE
     */
    public void handleMessage(ServerMessage message) {
        try {
            switch (message.getMessageType()) {
                // 요청 메시지
                case PAYMENT_REQUEST:
                    handlePaymentRequest(message);
                    break;
                    
                case SAVINGS_REQUEST:
                    handleSavingsRequest(message);
                    break;
                    
                // 완료 메시지
                case PAYMENT_COMPLETE:
                    handlePaymentComplete(message);
                    break;
                    
                case SAVINGS_COMPLETE:
                    handleSavingsComplete(message);
                    break;
                    
                // 기타 알림 메시지
                case ROOM_STATUS_CHANGED:
                    handleRoomStatusChanged(message);
                    break;
                    
                case MEMBER_JOINED:
                    handleMemberJoined(message);
                    break;
                    
                case MEMBER_LEFT:
                    handleMemberLeft(message);
                    break;
                    
                default:
                    log.warn("⚠️ 처리되지 않은 메시지 타입: {}", message.getMessageType());
            }
        } catch (Exception e) {
            log.error("❌ 메시지 처리 실패: messageType={}, messageId={}", 
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
    
    /**
     * Main 서버의 정산 완료 알림 처리
     * PAYMENT_COMPLETE 메시지 타입 처리
     */
    private void handlePaymentComplete(ServerMessage message) {
        Map<String, Object> payload = message.getPayload();
        String roomId = (String) payload.get("roomId");
        String settlementId = (String) payload.get("settlementId");
        String status = (String) payload.get("status");
        List<Map<String, Object>> paymentResults = (List<Map<String, Object>>) payload.get("paymentResults");
        String totalAmount = (String) payload.get("totalAmount");
        String completionMessage = (String) payload.get("message");
        
        log.info("✅ PAYMENT_COMPLETE 수신: roomId={}, settlementId={}, status={}", 
            roomId, settlementId, status);
        
        // 정산 완료 메시지를 해당 방에 브로드캐스트
        Map<String, Object> notification = Map.of(
            "type", "PAYMENT_COMPLETE",
            "roomId", roomId,
            "settlementId", settlementId,
            "status", status,
            "paymentResults", paymentResults,
            "totalAmount", totalAmount,
            "message", completionMessage,
            "timestamp", message.getTimestamp()
        );
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/payment", notification);
        
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
     * Main 서버의 정산 요청 브로드캐스트 처리
     * PAYMENT_REQUEST 메시지 타입 처리
     */
    public void handlePaymentRequest(ServerMessage message) {
        try {
            log.info("💰 정산 요청 브로드캐스트 처리 시작: messageId={}", message.getMessageId());
            
            Map<String, Object> payload = message.getPayload();
            log.info("🔍 Payload 내용: {}", payload);
            
            String settlementId = (String) payload.get("settlementId");
            String roomId = (String) payload.get("roomId");
            
            // 요청자 정보 추출
            Long requesterId = getLongFromPayload(payload, "requesterId", null);
            String requesterName = (String) payload.get("requesterName");
            String requesterStudentId = (String) payload.get("requesterStudentId");
            String requesterProfileImg = (String) payload.get("requesterProfileImg");
            
            // requestAmount 안전한 변환 (Redis Stream에서는 String으로 전달됨)
            Integer requestAmount = getIntegerFromPayload(payload, "requestAmount", 15000);
            
            log.info("🔍 추출된 데이터: settlementId={}, roomId={}, requesterId={}, requester={}, studentId={}, amount={}", 
                settlementId, roomId, requesterId, requesterName, requesterStudentId, requestAmount);
            
            log.info("💰 PAYMENT_REQUEST 수신: settlementId={}, roomId={}, requester={}", 
                settlementId, roomId, requesterName);
            
            // 정산 데이터 생성 (PaymentRequestData 사용)
            PaymentRequestData paymentData = PaymentRequestData.builder()
                .settlementId(settlementId)
                .roomId(roomId)
                .requesterId(requesterId)
                .requesterName(requesterName)
                .requesterStudentId(requesterStudentId)
                .requesterProfileImg(requesterProfileImg)
                .requestAmount(requestAmount)
                .settlementUrl("/main/settlement/" + settlementId)
                .build();
            
            log.info("🔍 PaymentRequestData 생성 완료: {}", paymentData);
            
            // 정산 메시지 생성 및 방에 브로드캐스트
            log.info("🔍 broadcastPaymentMessage 호출 시작");
            broadcastPaymentMessage(roomId, paymentData);
            log.info("🔍 broadcastPaymentMessage 호출 완료");
            
        } catch (Exception e) {
            log.error("❌ 정산 브로드캐스트 처리 실패: messageId={}", message.getMessageId(), e);
        }
    }
    
    private void broadcastPaymentMessage(String roomId, PaymentRequestData paymentData) {
        try {
            String messageId = UUID.randomUUID().toString();
            
            // 1. 금융 메시지 객체 생성
            ChatMessage chatMessage = ChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .senderId(paymentData.getRequesterId() != null ? paymentData.getRequesterId().toString() : "system")
                .studentId(paymentData.getRequesterStudentId() != null ? paymentData.getRequesterStudentId() : "SYSTEM")
                .senderName(paymentData.getRequesterName())
                .profileImageUrl(paymentData.getRequesterProfileImg())
                .content(String.format("%s님이 이체하기를 요청했습니다!", paymentData.getRequesterName()))
                .messageType(ChatMessage.MessageType.PAYMENT_REQUEST)
                .timestamp(LocalDateTime.now())
                .paymentRequestData(paymentData)
                .paymentCompleteData(null)
                .emergencyFallback(false)
                .build();
            
            // 2. 안전한 저장 (재시도 + Redis 백업)
            boolean saveSuccess = financialMessageService.saveFinancialMessageSafely(chatMessage);
            
            if (saveSuccess) {
                // 3. 저장/백업 성공 시 WebSocket 브로드캐스트
                ChatMessageResponse settlementMessage = createPaymentResponse(messageId, roomId, chatMessage, paymentData);
                messagingTemplate.convertAndSend("/topic/room/" + roomId, settlementMessage);
                
                log.info("✅ 정산 메시지 처리 완료 (MongoDB 저장 또는 Redis 백업): roomId={}, settlementId={}, messageId={}", 
                    roomId, paymentData.getSettlementId(), messageId);
                    
            } else {
                // 4. 완전 실패 시에만 에러 메시지 전송 (MongoDB, Redis 모두 실패)
                sendErrorMessageToRoom(roomId, "시스템 오류로 정산 요청을 처리할 수 없습니다. 관리자에게 문의해주세요.");
                log.error("💥 정산 메시지 완전 실패 (MongoDB + Redis 모두 실패): roomId={}, settlementId={}", 
                    roomId, paymentData.getSettlementId());
            }
                
        } catch (Exception e) {
            log.error("❌ 정산 메시지 처리 중 예상치 못한 오류: roomId={}, settlementId={}", 
                roomId, paymentData.getSettlementId(), e);
            sendErrorMessageToRoom(roomId, "시스템 오류가 발생했습니다. 관리자에게 문의해주세요.");
        }
    }
    
    private ChatMessageResponse createPaymentResponse(String messageId, String roomId, 
                                                     ChatMessage chatMessage, PaymentRequestData paymentData) {
        return ChatMessageResponse.builder()
            .messageId(messageId)
            .roomId(roomId)
            .senderId("system")
            .senderName("시스템")
            .content(chatMessage.getContent())
            .messageType("PAYMENT_REQUEST")
            .timestamp(chatMessage.getTimestamp())
            .paymentRequestData(PaymentRequestData.builder()
                .settlementId(paymentData.getSettlementId())
                .roomId(paymentData.getRoomId())
                .requesterName(paymentData.getRequesterName())
                .requestAmount(paymentData.getRequestAmount())
                .settlementUrl(paymentData.getSettlementUrl())
                .build())
            .build();
    }
    
    private void sendErrorMessageToRoom(String roomId, String errorMessage) {
        Map<String, Object> errorNotification = Map.of(
            "type", "SYSTEM_ERROR",
            "message", errorMessage,
            "timestamp", LocalDateTime.now(),
            "severity", "warning"
        );
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/error", errorNotification);
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
    
    /**
     * Payload에서 Long 값을 안전하게 추출
     */
    private Long getLongFromPayload(Map<String, Object> payload, String key, Long defaultValue) {
        Object value = payload.get(key);
        if (value == null) return defaultValue;
        
        try {
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof Integer) {
                return ((Integer) value).longValue();
            } else if (value instanceof String) {
                return Long.parseLong((String) value);
            } else {
                return Long.parseLong(value.toString());
            }
        } catch (NumberFormatException e) {
            log.warn("⚠️ {} 변환 실패, 기본값 사용: {} -> {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 적금 완료 알림 처리
     */
    /**
     * Main 서버의 적금 완료 알림 처리
     * SAVINGS_COMPLETE 메시지 타입 처리
     */
    private void handleSavingsComplete(ServerMessage message) {
        Map<String, Object> payload = message.getPayload();
        String roomId = (String) payload.get("roomId");
        String savingsId = (String) payload.get("savingsId");
        String status = (String) payload.get("status");
        List<Map<String, Object>> savingsResults = (List<Map<String, Object>>) payload.get("savingsResults");
        String totalAmount = (String) payload.get("totalAmount");
        String completionMessage = (String) payload.get("message");
        
        log.info("✅ SAVINGS_COMPLETE 수신: roomId={}, savingsId={}, status={}", 
            roomId, savingsId, status);
        
        // 적금 완료 메시지를 해당 방에 브로드캐스트
        Map<String, Object> notification = Map.of(
            "type", "SAVINGS_COMPLETE",
            "roomId", roomId,
            "savingsId", savingsId,
            "status", status,
            "savingsResults", savingsResults,
            "totalAmount", totalAmount,
            "message", completionMessage,
            "timestamp", message.getTimestamp()
        );
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/savings", notification);
        
        // 개별 사용자에게도 결과 전송
        for (Map<String, Object> result : savingsResults) {
            String userId = (String) result.get("userId");
            String userStatus = (String) result.get("status");
            String userMessage = (String) result.get("message");
            
            Map<String, Object> userNotification = Map.of(
                "type", "PERSONAL_SAVINGS_RESULT",
                "savingsId", savingsId,
                "status", userStatus,
                "message", userMessage,
                "timestamp", message.getTimestamp()
            );
            
            messagingTemplate.convertAndSendToUser(userId, "/queue/savings", userNotification);
        }
    }
    
    /**
     * Main 서버의 적금 요청 브로드캐스트 처리
     * SAVINGS_REQUEST 메시지 타입 처리
     */
    public void handleSavingsRequest(ServerMessage message) {
        try {
            log.info("💰 적금 요청 브로드캐스트 처리 시작: messageId={}", message.getMessageId());
            
            Map<String, Object> payload = message.getPayload();
            log.info("🔍 Payload 내용: {}", payload);
            
            String savingsId = (String) payload.get("settlementId");  // settlementId를 savingsId로 사용
            String roomId = (String) payload.get("roomId");
            
            // 요청자 정보 추출
            Long requesterId = getLongFromPayload(payload, "requesterId", null);
            String requesterName = (String) payload.get("requesterName");
            String requesterStudentId = (String) payload.get("requesterStudentId");
            String requesterProfileImg = (String) payload.get("requesterProfileImg");
            
            Integer requestAmount = getIntegerFromPayload(payload, "requestAmount", 50000);
            
            log.info("🔍 추출된 적금 데이터: savingsId={}, roomId={}, requesterId={}, requester={}, amount={}", 
                savingsId, roomId, requesterId, requesterName, requestAmount);
            
            log.info("💰 SAVINGS_REQUEST 수신: savingsId={}, roomId={}, requester={}", 
                savingsId, roomId, requesterName);
            
            // 적금 데이터 생성
            PaymentRequestData savingsData = PaymentRequestData.builder()
                .settlementId(savingsId)
                .roomId(roomId)
                .requesterId(requesterId)
                .requesterName(requesterName)
                .requesterStudentId(requesterStudentId)
                .requesterProfileImg(requesterProfileImg)
                .requestAmount(requestAmount)
                .settlementUrl("/main/savings/" + savingsId)
                .build();
            
            log.info("🔍 SavingsData 생성 완료: {}", savingsData);
            
            // 적금 메시지 생성 및 방에 브로드캐스트
            log.info("🔍 broadcastSavingsMessage 호출 시작");
            broadcastSavingsMessage(roomId, savingsData);
            log.info("🔍 broadcastSavingsMessage 호출 완료");
            
        } catch (Exception e) {
            log.error("❌ 적금 브로드캐스트 처리 실패: messageId={}", message.getMessageId(), e);
        }
    }
    
    /**
     * 적금 메시지 브로드캐스트 (안전한 저장 + 브로드캐스트)
     */
    private void broadcastSavingsMessage(String roomId, PaymentRequestData savingsData) {
        try {
            String messageId = UUID.randomUUID().toString();
            
            // 1. 적금 메시지 객체 생성
            ChatMessage chatMessage = ChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .senderId(savingsData.getRequesterId() != null ? savingsData.getRequesterId().toString() : "system")
                .studentId(savingsData.getRequesterStudentId() != null ? savingsData.getRequesterStudentId() : "SYSTEM")
                .senderName(savingsData.getRequesterName())
                .profileImageUrl(savingsData.getRequesterProfileImg())
                .content(String.format("%s님이 적금 참여를 요청했습니다!", savingsData.getRequesterName()))
                .messageType(ChatMessage.MessageType.SAVINGS_REQUEST)
                .timestamp(LocalDateTime.now())
                .paymentRequestData(savingsData)
                .paymentCompleteData(null)
                .emergencyFallback(false)
                .build();
            
            // 2. 안전한 저장 (재시도 + Redis 백업)
            boolean saveSuccess = financialMessageService.saveFinancialMessageSafely(chatMessage);
            
            if (saveSuccess) {
                // 3. 저장/백업 성공 시 WebSocket 브로드캐스트
                ChatMessageResponse savingsMessage = ChatMessageResponse.builder()
                    .messageId(messageId)
                    .roomId(roomId)
                    .senderId(savingsData.getRequesterId() != null ? savingsData.getRequesterId().toString() : "system")
                    .senderName(savingsData.getRequesterName())
                    .content(chatMessage.getContent())
                    .messageType("SAVINGS_REQUEST")
                    .timestamp(chatMessage.getTimestamp())
                    .paymentRequestData(savingsData)
                    .build();
                
                messagingTemplate.convertAndSend("/topic/room/" + roomId, savingsMessage);
                
                log.info("✅ 적금 메시지 처리 완료 (MongoDB 저장 또는 Redis 백업): roomId={}, savingsId={}, messageId={}", 
                    roomId, savingsData.getSettlementId(), messageId);
                    
            } else {
                // 4. 완전 실패 시에만 에러 메시지 전송
                sendErrorMessageToRoom(roomId, "시스템 오류로 적금 요청을 처리할 수 없습니다. 관리자에게 문의해주세요.");
                log.error("💥 적금 메시지 완전 실패 (MongoDB + Redis 모두 실패): roomId={}, savingsId={}", 
                    roomId, savingsData.getSettlementId());
            }
                
        } catch (Exception e) {
            log.error("❌ 적금 메시지 처리 중 예상치 못한 오류: roomId={}, savingsId={}", 
                roomId, savingsData.getSettlementId(), e);
            sendErrorMessageToRoom(roomId, "시스템 오류가 발생했습니다. 관리자에게 문의해주세요.");
        }
    }
}