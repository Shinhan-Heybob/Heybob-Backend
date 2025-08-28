package com.shinhan.heybob.domain.test.controller;

import com.shinhan.heybob.domain.notification.dto.ChatBroadcastRequest;
import com.shinhan.heybob.domain.test.service.TestService;
import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;
import com.shinhan.heybob.domain.meal.service.MealAppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/test")  // context-path가 /api이므로 /test만 사용
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final TestService testService;
    private final MealAppointmentService mealAppointmentService;

    @PostMapping("/send-settlement-broadcast")
    public ResponseEntity<Map<String, Object>> sendSettlementBroadcast(@RequestBody ChatBroadcastRequest request) {
        try {
            log.info("🧪 테스트 정산 브로드캐스트 요청: {}", request);
            
            request.setType(ChatBroadcastRequest.BroadcastType.PAYMENT);
            String messageId = testService.sendSettlementBroadcast(request);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", messageId,
                "message", "정산 브로드캐스트가 Redis Stream으로 전송되었습니다"
            ));
            
        } catch (Exception e) {
            log.error("❌ 테스트 정산 브로드캐스트 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    @PostMapping("/send-savings-broadcast")
    public ResponseEntity<Map<String, Object>> sendSavingsBroadcast(@RequestBody ChatBroadcastRequest request) {
        try {
            log.info("🧪 테스트 적금 브로드캐스트 요청: {}", request);
            
            request.setType(ChatBroadcastRequest.BroadcastType.SAVINGS);
            String messageId = testService.sendSavingsBroadcast(request);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", messageId,
                "message", "적금 브로드캐스트가 Redis Stream으로 전송되었습니다"
            ));
            
        } catch (Exception e) {
            log.error("❌ 테스트 적금 브로드캐스트 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * 테스트용 밥약 생성 API (인증 우회)
     */
    @PostMapping("/create-meal-appointment")
    public ResponseEntity<MealAppointmentDetailResponse> createTestMealAppointment(@RequestBody CreateMealAppointmentRequest request) {
        try {
            log.info("🧪 테스트 밥약 생성 요청: {}", request);
            
            // 실제 MealAppointmentService를 사용하여 밥약 생성
            MealAppointmentDetailResponse response = mealAppointmentService.createMealAppointment(request);
            
            log.info("✅ 테스트 밥약 생성 성공: 밥약ID={}, 채팅방ID={}", response.getId(), response.getChatRoomId());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("❌ 테스트 밥약 생성 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 특정 채팅방으로 정산 메시지 전송 (밥약 채팅방 테스트용)
     */
    @PostMapping("/send-settlement-to-room")
    public ResponseEntity<Map<String, Object>> sendSettlementToRoom(@RequestBody Map<String, Object> request) {
        try {
            String chatRoomId = (String) request.get("chatRoomId");
            String requesterName = (String) request.get("requesterName");
            Integer requestAmount = (Integer) request.get("requestAmount");
            
            String settlementId = "SETTLEMENT_" + System.currentTimeMillis();
            
            ChatBroadcastRequest broadcastRequest = ChatBroadcastRequest.builder()
                .settlementId(settlementId)
                .roomId(chatRoomId)
                .requesterName(requesterName)
                .requestAmount(requestAmount)
                .message(requesterName + "님이 정산을 요청했습니다")
                .type(ChatBroadcastRequest.BroadcastType.PAYMENT)
                .build();
            
            String messageId = testService.sendSettlementBroadcast(broadcastRequest);
            
            log.info("✅ 밥약 채팅방 정산 메시지 전송: messageId={}, chatRoomId={}, settlementId={}", 
                messageId, chatRoomId, settlementId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", messageId,
                "settlementId", settlementId,
                "chatRoomId", chatRoomId,
                "message", "정산 요청이 채팅방으로 전송되었습니다"
            ));
            
        } catch (Exception e) {
            log.error("❌ 밥약 채팅방 정산 메시지 전송 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * 테스트용 정산 완료 알림 전송 (HTML 테스트에서 사용)
     */
    @PostMapping("/chat/payment-complete")
    public ResponseEntity<Map<String, Object>> sendPaymentComplete(@RequestBody Map<String, Object> request) {
        try {
            String roomId = (String) request.get("roomId");
            String senderName = (String) request.get("senderName");
            Integer completedAmount = (Integer) request.get("completedAmount");
            String message = (String) request.get("message");
            
            log.info("🧪 테스트 정산 완료 요청: roomId={}, senderName={}, amount={}", roomId, senderName, completedAmount);
            
            ChatBroadcastRequest broadcastRequest = ChatBroadcastRequest.builder()
                .roomId(roomId)
                .requesterName(senderName)
                .requestAmount(completedAmount)
                .message(message != null ? message : senderName + "님의 정산이 완료되었습니다")
                .type(ChatBroadcastRequest.BroadcastType.PAYMENT_COMPLETE)
                .build();
            
            String messageId = testService.sendPaymentCompleteBroadcast(broadcastRequest);
            
            log.info("✅ 테스트 정산 완료 알림 전송 성공: messageId={}, roomId={}", messageId, roomId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", messageId,
                "message", "정산 완료 알림이 전송되었습니다"
            ));
            
        } catch (Exception e) {
            log.error("❌ 테스트 정산 완료 알림 전송 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * 테스트용 적금 완료 알림 전송 (HTML 테스트에서 사용)
     */
    @PostMapping("/chat/savings-complete")
    public ResponseEntity<Map<String, Object>> sendSavingsComplete(@RequestBody Map<String, Object> request) {
        try {
            String roomId = (String) request.get("roomId");
            String senderName = (String) request.get("senderName");
            Integer completedAmount = (Integer) request.get("completedAmount");
            String message = (String) request.get("message");
            
            log.info("🧪 테스트 적금 완료 요청: roomId={}, senderName={}, amount={}", roomId, senderName, completedAmount);
            
            ChatBroadcastRequest broadcastRequest = ChatBroadcastRequest.builder()
                .roomId(roomId)
                .requesterName(senderName)
                .requestAmount(completedAmount)
                .message(message != null ? message : senderName + "님의 적금이 완료되었습니다")
                .type(ChatBroadcastRequest.BroadcastType.SAVINGS_COMPLETE)
                .build();
            
            String messageId = testService.sendSavingsCompleteBroadcast(broadcastRequest);
            
            log.info("✅ 테스트 적금 완료 알림 전송 성공: messageId={}, roomId={}", messageId, roomId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", messageId,
                "message", "적금 완료 알림이 전송되었습니다"
            ));
            
        } catch (Exception e) {
            log.error("❌ 테스트 적금 완료 알림 전송 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}