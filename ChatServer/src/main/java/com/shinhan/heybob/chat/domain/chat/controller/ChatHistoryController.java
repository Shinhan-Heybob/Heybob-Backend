package com.shinhan.heybob.chat.domain.chat.controller;

import com.shinhan.heybob.chat.domain.chat.dto.ChatHistoryResponse;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.service.ChatService;
import com.shinhan.heybob.chat.domain.cafeteria.service.CafeteriaService;
import com.shinhan.heybob.chat.global.error.ChatException;
import com.shinhan.heybob.chat.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryController {

    private final ChatService chatService;

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ChatHistoryResponse> getMessages(
            @PathVariable String roomId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "X-User-Id", required = false) String userId  // 테스트용으로 옵션 처리
    ) {
        // 입력 검증
        if (roomId == null || roomId.trim().isEmpty()) {
            throw new ChatException(ErrorCode.ROOM_NOT_FOUND);
        }
        if (limit <= 0 || limit > 100) {
            throw new ChatException(ErrorCode.INVALID_REQUEST);
        }
        // 테스트용: userId가 없으면 기본값 사용
        if (userId == null || userId.trim().isEmpty()) {
            userId = "test-user";
        }
        
        log.info("채팅 히스토리 요청: roomId={}, userId={}, before={}, limit={}", roomId, userId, before, limit);
        
        ChatHistoryResponse response = chatService.getChatHistory(roomId, before, limit);
        
        if (response == null) {
            log.warn("채팅 히스토리가 null로 반환됨: roomId={}", roomId);
            response = ChatHistoryResponse.builder()
                    .messages(List.of())
                    .totalCount(0)
                    .hasMore(false)
                    .lastMessageId(null)
                    .build();
        }
        
        log.info("채팅 히스토리 응답: roomId={}, count={}, lastMessageId={}, hasMore={}", 
                roomId, response.getTotalCount(), response.getLastMessageId(), response.isHasMore());
        
        return ResponseEntity.ok(response);
    }
    
    // 기존 API 호환성을 위한 레거시 엔드포인트 (필요시 유지)
    @GetMapping("/rooms/{roomId}/messages/legacy")
    public ResponseEntity<List<ChatMessageResponse>> getMessagesLegacy(
            @PathVariable String roomId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        // 입력 검증
        if (roomId == null || roomId.trim().isEmpty()) {
            throw new ChatException(ErrorCode.ROOM_NOT_FOUND);
        }
        if (limit <= 0 || limit > 100) {
            throw new ChatException(ErrorCode.INVALID_REQUEST);
        }
        // 테스트용: userId가 없으면 기본값 사용
        if (userId == null || userId.trim().isEmpty()) {
            userId = "test-user";
        }
        
        log.info("레거시 채팅 히스토리 요청: roomId={}, userId={}, before={}, limit={}", roomId, userId, before, limit);
        
        List<ChatMessageResponse> messages;
        
        if (before != null) {
            messages = chatService.getMessagesBefore(roomId, before, limit);
        } else {
            messages = chatService.getRecentMessages(roomId, limit);
        }
        
        log.info("레거시 채팅 히스토리 응답: roomId={}, count={}", roomId, messages.size());
        return ResponseEntity.ok(messages);
    }
}