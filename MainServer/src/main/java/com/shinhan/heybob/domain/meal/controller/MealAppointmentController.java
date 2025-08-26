package com.shinhan.heybob.domain.meal.controller;

import com.shinhan.heybob.common.chat.dto.ChatBroadcastRequest;
import com.shinhan.heybob.common.chat.dto.ServerMessage;
import com.shinhan.heybob.common.chat.service.ChatMessageService;
import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.request.ScheduleComparisonRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;
import com.shinhan.heybob.domain.meal.dto.response.ScheduleComparisonResponse;
import com.shinhan.heybob.domain.meal.service.ChatIntegrationService;
import com.shinhan.heybob.domain.meal.service.MealAppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/meal-appointments")  // context-path가 /api이므로 /meal-appointments만 사용
@RequiredArgsConstructor
public class MealAppointmentController {

    private final MealAppointmentService mealAppointmentService;
    private final ChatIntegrationService chatIntegrationService;
    private final ChatMessageService chatMessageService;

    @PostMapping("/schedules/compare")
    public ResponseEntity<ScheduleComparisonResponse> compareSchedules(
            @RequestBody @Valid ScheduleComparisonRequest request) {
        ScheduleComparisonResponse response = mealAppointmentService.compareSchedules(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<MealAppointmentDetailResponse> createMealAppointment(
            @RequestBody @Valid CreateMealAppointmentRequest request) {
        MealAppointmentDetailResponse response = mealAppointmentService.createMealAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{appointmentId}")
    public ResponseEntity<MealAppointmentDetailResponse> getMealAppointment(
            @PathVariable Long appointmentId) {
        MealAppointmentDetailResponse response = mealAppointmentService.getMealAppointment(appointmentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<MealAppointmentDetailResponse>> getUserMealAppointments(
            @RequestParam Long userId) {
        List<MealAppointmentDetailResponse> response = mealAppointmentService.getUserMealAppointments(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 밥약 정산 브로드캐스트 전송
     */
    @PostMapping("/{appointmentId}/settlement-broadcast")
    public ResponseEntity<Map<String, Object>> sendSettlementBroadcast(
            @PathVariable Long appointmentId,
            @RequestBody Map<String, Object> request) {
        try {
            // 밥약 정보 조회
            MealAppointmentDetailResponse appointment = mealAppointmentService.getMealAppointment(appointmentId);
            
            String settlementId = (String) request.get("settlementId");
            String requesterName = (String) request.get("requesterName");
            Integer requestAmount = (Integer) request.get("requestAmount");
            String message = (String) request.get("message");
            
            // 채팅방 ID를 roomId로 사용
            String roomId = appointment.getChatRoomId().toString();
            
            // 정산 브로드캐스트 전송
            String messageId = chatIntegrationService.sendSettlementBroadcast(
                settlementId, 
                roomId, 
                requesterName, 
                requestAmount, 
                message
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", messageId,
                "appointmentId", appointmentId,
                "chatRoomId", roomId,
                "message", "정산 브로드캐스트가 전송되었습니다"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 밥약 적금 브로드캐스트 전송
     */
    @PostMapping("/{appointmentId}/savings-broadcast")
    public ResponseEntity<Map<String, Object>> sendSavingsBroadcast(
            @PathVariable Long appointmentId,
            @RequestBody Map<String, Object> request) {
        try {
            // 밥약 정보 조회
            MealAppointmentDetailResponse appointment = mealAppointmentService.getMealAppointment(appointmentId);
            
            String savingsId = (String) request.get("savingsId");
            String requesterName = (String) request.get("requesterName");
            Integer requestAmount = (Integer) request.get("requestAmount");
            String message = (String) request.get("message");
            
            // 채팅방 ID를 roomId로 사용
            String roomId = appointment.getChatRoomId().toString();
            
            // 적금 브로드캐스트 요청 생성
            ChatBroadcastRequest broadcastRequest = ChatBroadcastRequest.builder()
                .settlementId(savingsId)  // savingsId를 settlementId 필드로 사용
                .roomId(roomId)
                .requesterId(999L)  // 더미 ID
                .requesterName(requesterName)
                .requesterStudentId("2024999")  // 더미 학번
                .requesterProfileImg("/profile/default.jpg")  // 더미 프로필
                .requestAmount(requestAmount)
                .message(message)
                .type(ChatBroadcastRequest.BroadcastType.SAVINGS)
                .build();
            
            // 적금 브로드캐스트 전송
            String messageId = chatMessageService.sendSavingsBroadcast(broadcastRequest);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", messageId,
                "appointmentId", appointmentId,
                "chatRoomId", roomId,
                "savingsId", savingsId,
                "message", "적금 브로드캐스트가 전송되었습니다"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 정산 완료 알림 전송
     */
    @PostMapping("/{appointmentId}/payment-complete")
    public ResponseEntity<Map<String, Object>> sendPaymentComplete(
            @PathVariable Long appointmentId,
            @RequestBody Map<String, Object> request) {
        try {
            // 밥약 정보 조회
            MealAppointmentDetailResponse appointment = mealAppointmentService.getMealAppointment(appointmentId);
            String roomId = appointment.getChatRoomId().toString();
            
            // 완료 알림 메시지 생성
            Map<String, Object> payload = new HashMap<>();
            payload.put("roomId", roomId);
            payload.put("settlementId", request.get("settlementId"));
            payload.put("status", request.get("status"));
            payload.put("paymentResults", request.get("paymentResults"));
            payload.put("totalAmount", request.get("totalAmount"));
            payload.put("message", request.get("message"));
            
            ServerMessage message = ServerMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(ServerMessage.MessageType.PAYMENT_COMPLETE)
                .sourceServer("MAIN")
                .targetServer("CHAT")
                .timestamp(LocalDateTime.now())
                .payload(payload)
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
            
            String messageId = chatMessageService.sendMessage(message);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", messageId,
                "message", "정산 완료 알림이 전송되었습니다"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 적금 완료 알림 전송
     */
    @PostMapping("/{appointmentId}/savings-complete")
    public ResponseEntity<Map<String, Object>> sendSavingsComplete(
            @PathVariable Long appointmentId,
            @RequestBody Map<String, Object> request) {
        try {
            // 밥약 정보 조회
            MealAppointmentDetailResponse appointment = mealAppointmentService.getMealAppointment(appointmentId);
            String roomId = appointment.getChatRoomId().toString();
            
            // 완료 알림 메시지 생성
            Map<String, Object> payload = new HashMap<>();
            payload.put("roomId", roomId);
            payload.put("savingsId", request.get("savingsId"));
            payload.put("status", request.get("status"));
            payload.put("savingsResults", request.get("savingsResults"));
            payload.put("totalAmount", request.get("totalAmount"));
            payload.put("message", request.get("message"));
            
            ServerMessage message = ServerMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(ServerMessage.MessageType.SAVINGS_COMPLETE)
                .sourceServer("MAIN")
                .targetServer("CHAT")
                .timestamp(LocalDateTime.now())
                .payload(payload)
                .retryCount(0)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
            
            String messageId = chatMessageService.sendMessage(message);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", messageId,
                "message", "적금 완료 알림이 전송되었습니다"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}