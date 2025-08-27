package com.shinhan.heybob.domain.meal.controller;

import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentIdResponse;
import com.shinhan.heybob.domain.meal.service.ChatIntegrationService;
import com.shinhan.heybob.domain.meal.service.MealAppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/meal-appointments")  // context-path가 /api이므로 /meal-appointments만 사용
@RequiredArgsConstructor
public class MealAppointmentController {

    private final MealAppointmentService mealAppointmentService;
    private final ChatIntegrationService chatIntegrationService;


    @PostMapping
    public ResponseEntity<MealAppointmentIdResponse> createMealAppointment(
            @RequestBody @Valid CreateMealAppointmentRequest request) {
        MealAppointmentDetailResponse response = mealAppointmentService.createMealAppointment(request);
        MealAppointmentIdResponse idResponse = MealAppointmentIdResponse.builder()
                .id(response.getId())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(idResponse);
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


}