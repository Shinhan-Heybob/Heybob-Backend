package com.shinhan.heybob.chat.domain.communication.handler;

import com.shinhan.heybob.chat.domain.communication.dto.ServerMessage;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import com.shinhan.heybob.chat.domain.chat.dto.UiState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageHandler {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    // 활성 정산 요청들을 메모리에 임시 저장 (실제로는 Redis에 저장해야 함)
    private final Map<String, SettlementData> activeSettlements = new ConcurrentHashMap<>();
    
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
                    
                case SETTLEMENT_COMPLETED:
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
     * Main 서버의 정산 브로드캐스트 요청 처리
     */
    public void handleSettlementBroadcast(ServerMessage message) {
        try {
            Map<String, Object> payload = message.getPayload();
            String settlementId = (String) payload.get("settlementId");
            String roomId = (String) payload.get("roomId");
            String requesterId = (String) payload.get("requesterId");
            String requesterName = (String) payload.get("requesterName");
            List<String> targetUserIds = (List<String>) payload.get("targetUserIds");
            Integer perPersonAmount = (Integer) payload.get("perPersonAmount");
            String note = (String) payload.get("note");
            String expiryTimeStr = (String) payload.get("expiryTime");
            
            log.info("💰 Main 서버로부터 정산 브로드캐스트 요청: settlementId={}, roomId={}, requester={}", 
                settlementId, roomId, requesterName);
            
            // SettlementData 생성
            LocalDateTime expiryTime = LocalDateTime.parse(expiryTimeStr);
            Map<String, SettlementData.SettlementStatus> participantStatus = new HashMap<>();
            
            // 모든 참여자의 초기 상태 설정
            for (String userId : targetUserIds) {
                participantStatus.put(userId, SettlementData.SettlementStatus.builder()
                    .status("pending")
                    .build());
            }
            
            SettlementData settlementData = SettlementData.builder()
                .settlementId(settlementId)
                .roomId(roomId)
                .note(note)
                .totalAmount(perPersonAmount * targetUserIds.size())
                .perPersonAmount(perPersonAmount)
                .participants(targetUserIds)
                .expiryTime(expiryTime)
                .participantStatus(participantStatus)
                .build();
            
            // 활성 정산으로 등록
            activeSettlements.put(settlementId, settlementData);
            
            // 각 사용자별로 맞춤형 정산 메시지 생성 및 전송
            broadcastSettlementToUsers(roomId, requesterId, requesterName, settlementData);
            
        } catch (Exception e) {
            log.error("❌ 정산 브로드캐스트 처리 실패: messageId={}", message.getMessageId(), e);
        }
    }
    
    private void broadcastSettlementToUsers(String roomId, String requesterId, String requesterName, 
                                           SettlementData settlementData) {
        for (String userId : settlementData.getParticipants()) {
            try {
                // 사용자별 UI 상태 생성
                boolean isRequester = userId.equals(requesterId);
                UiState uiState = createSettlementUiState(userId, isRequester, settlementData);
                
                // 정산 메시지 생성
                ChatMessageResponse settlementMessage = ChatMessageResponse.builder()
                    .messageId(UUID.randomUUID().toString())
                    .roomId(roomId)
                    .senderId(requesterId)
                    .senderName(requesterName)
                    .content(String.format("%s님이 정산을 요청했습니다.\n💰 %s\n1인당 %,d원", 
                        requesterName, settlementData.getNote(), settlementData.getPerPersonAmount()))
                    .messageType("PAYMENT_REQUEST")
                    .timestamp(LocalDateTime.now())
                    .settlementData(settlementData)
                    .uiState(uiState)
                    .build();
                
                // 해당 방의 모든 사용자에게 브로드캐스트
                messagingTemplate.convertAndSend("/topic/room/" + roomId, settlementMessage);
                
                log.debug("📨 정산 메시지 전송: userId={}, settlementId={}", userId, settlementData.getSettlementId());
                
            } catch (Exception e) {
                log.error("❌ 사용자별 정산 메시지 전송 실패: userId={}, settlementId={}", 
                    userId, settlementData.getSettlementId(), e);
            }
        }
    }
    
    private UiState createSettlementUiState(String userId, boolean isRequester, SettlementData settlementData) {
        boolean isExpired = LocalDateTime.now().isAfter(settlementData.getExpiryTime());
        
        SettlementData.SettlementStatus userStatus = settlementData.getParticipantStatus().get(userId);
        String userResponseStatus = userStatus != null ? userStatus.getStatus() : "pending";
        
        List<String> availableActions = new ArrayList<>();
        if (isExpired) {
            availableActions.add("view_details");
        } else if (isRequester) {
            availableActions.addAll(Arrays.asList("cancel", "view_details"));
        } else {
            if ("pending".equals(userResponseStatus)) {
                availableActions.addAll(Arrays.asList("accept", "reject", "view_details"));
            } else {
                availableActions.add("view_details");
            }
        }
        
        return UiState.builder()
            .isRequester(isRequester)
            .userResponseStatus(userResponseStatus)
            .availableActions(availableActions)
            .isExpired(isExpired)
            .build();
    }
    
    // 활성 정산 조회 (다른 서비스에서 사용)
    public SettlementData getActiveSettlement(String settlementId) {
        return activeSettlements.get(settlementId);
    }
    
    // 활성 정산 제거 (정산 완료 후)
    public void removeActiveSettlement(String settlementId) {
        activeSettlements.remove(settlementId);
        log.debug("🗑️ 활성 정산 제거: settlementId={}", settlementId);
    }
}