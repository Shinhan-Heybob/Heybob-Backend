package com.shinhan.heybob.chat.domain.chat.controller;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import com.shinhan.heybob.chat.domain.chat.service.ChatService;
import com.shinhan.heybob.chat.domain.chat.service.SettlementService;
import com.shinhan.heybob.chat.global.error.ChatException;
import com.shinhan.heybob.chat.global.error.ErrorCode;
import com.shinhan.heybob.chat.global.error.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;
    private final SettlementService settlementService;

    @MessageMapping("/chat/{roomId}")
    public void sendMessage(
            @DestinationVariable String roomId,
            @Payload ChatMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        try {
            log.info("메시지 수신: roomId={}, content={}", roomId, request.getContent());
            
            // 입력 검증
            if (roomId == null || roomId.trim().isEmpty()) {
                throw new ChatException(ErrorCode.ROOM_NOT_FOUND);
            }
            if (request == null) {
                throw new ChatException(ErrorCode.INVALID_REQUEST);
            }
            
            // Main 서버에서 헤더로 사용자 정보 전달받음
            String userId = headerAccessor.getFirstNativeHeader("X-User-Id");
            String studentId = headerAccessor.getFirstNativeHeader("X-Student-Id");
            String userName = headerAccessor.getFirstNativeHeader("X-User-Name");
            String profileImageUrl = headerAccessor.getFirstNativeHeader("X-Profile-Image");
            
            // 개발용 우회 처리 (헤더가 없을 때)
            if (userId == null || userName == null) {
                log.info("헤더 없음 - 개발용 기본값 사용");
                userId = "20000622";
                studentId = "20000622";
                userName = "개발테스트사용자";
                profileImageUrl = "https://example.com/default-profile.jpg";
            }
            
            // 정산 버튼 상호작용 처리
            if (isSettlementInteraction(request.getMessageType())) {
                handleSettlementInteraction(roomId, userId, request);
            }
            
            // 메시지 처리 및 저장
            ChatMessageResponse response = chatService.processMessage(roomId, userId, studentId, userName, profileImageUrl, request);
            
            // 해당 방의 모든 구독자에게 브로드캐스트
            messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
            
            log.info("메시지 브로드캐스트 완료: roomId={}, messageId={}", roomId, response.getMessageId());
            
        } catch (ChatException e) {
            log.error("WebSocket 메시지 처리 실패: roomId={}, error={}", roomId, e.getMessage());
            // WebSocket에서는 직접 에러 응답을 보내거나 에러 토픽으로 전송할 수 있음
            messagingTemplate.convertAndSendToUser(
                headerAccessor.getSessionId(), 
                "/queue/errors", 
                ErrorResponse.of(e.getErrorCode())
            );
        } catch (Exception e) {
            log.error("WebSocket 예상치 못한 오류: roomId={}", roomId, e);
            messagingTemplate.convertAndSendToUser(
                headerAccessor.getSessionId(), 
                "/queue/errors", 
                ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        }
    }
    
    private boolean isSettlementInteraction(String messageType) {
        return "SETTLEMENT_ACCEPT".equals(messageType) || 
               "SETTLEMENT_REJECT".equals(messageType) || 
               "SETTLEMENT_CANCEL".equals(messageType);
    }
    
    private void handleSettlementInteraction(String roomId, String userId, ChatMessageRequest request) {
        try {
            String settlementId = request.getSettlementId();
            if (settlementId == null) {
                log.warn("정산 상호작용에 settlementId 없음: messageType={}", request.getMessageType());
                return;
            }
            
            String responseType = switch (request.getMessageType()) {
                case "SETTLEMENT_ACCEPT" -> "accepted";
                case "SETTLEMENT_REJECT" -> "rejected";
                case "SETTLEMENT_CANCEL" -> "cancelled";
                default -> null;
            };
            
            if (responseType != null) {
                SettlementData updatedSettlement = settlementService.updateSettlementResponse(settlementId, userId, responseType);
                
                // 정산 상태 업데이트를 방의 모든 사용자에게 브로드캐스트
                broadcastSettlementUpdate(roomId, updatedSettlement);
            }
            
        } catch (Exception e) {
            log.error("정산 상호작용 처리 실패: roomId={}, userId={}, messageType={}", roomId, userId, request.getMessageType(), e);
        }
    }
    
    private void broadcastSettlementUpdate(String roomId, SettlementData settlementData) {
        // 정산 상태 업데이트를 방의 모든 구독자에게 알림
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/settlement", settlementData);
        log.info("정산 상태 업데이트 브로드캐스트: roomId={}, settlementId={}", roomId, settlementData.getSettlementId());
    }
}