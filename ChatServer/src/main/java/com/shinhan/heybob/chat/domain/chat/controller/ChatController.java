package com.shinhan.heybob.chat.domain.chat.controller;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.service.ChatService;
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
}