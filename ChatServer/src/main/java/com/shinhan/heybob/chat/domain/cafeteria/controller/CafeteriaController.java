package com.shinhan.heybob.chat.domain.cafeteria.controller;

import com.shinhan.heybob.chat.domain.cafeteria.service.CafeteriaService;
import com.shinhan.heybob.chat.domain.chat.service.ChatService;
import com.shinhan.heybob.chat.global.error.ChatException;
import com.shinhan.heybob.chat.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class CafeteriaController {

    private final CafeteriaService cafeteriaService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/{roomId}/cafeteria")
    public ResponseEntity<Map<String, Object>> sendCafeteriaInfo(
            @PathVariable String roomId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        try {
            log.info("학식 정보 HTTP 요청: roomId={}, userId={}, userName={}", roomId, userId, userName);
            
            // 입력 검증
            if (roomId == null || roomId.trim().isEmpty()) {
                throw new ChatException(ErrorCode.ROOM_NOT_FOUND);
            }
            
            // 개발용 기본값 설정
            if (userId == null || userId.trim().isEmpty()) {
                userId = "test-user";
                userName = "테스트사용자";
            }
            
            // Redis에서 학식 정보 조회
            String cafeteriaInfo = cafeteriaService.getTodayCafeteriaInfo();
            
            // 학식 정보를 채팅 메시지로 처리하여 브로드캐스트
            var response = chatService.processCafeteriaInfo(
                roomId, 
                userId, 
                userId, // studentId로 사용
                userName != null ? java.net.URLDecoder.decode(userName, "UTF-8") : "익명사용자",
                "https://example.com/default.jpg", // 기본 프로필 이미지
                cafeteriaInfo
            );
            
            // 해당 방의 모든 구독자에게 브로드캐스트
            messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
            
            log.info("학식 정보 HTTP 브로드캐스트 완료: roomId={}, messageId={}", roomId, response.getMessageId());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "학식 정보가 전송되었습니다",
                "messageId", response.getMessageId()
            ));
            
        } catch (ChatException e) {
            log.error("학식 정보 HTTP 처리 실패: roomId={}, error={}", roomId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("학식 정보 HTTP 예상치 못한 오류: roomId={}", roomId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "서버 내부 오류가 발생했습니다"
            ));
        }
    }

}