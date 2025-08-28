package com.shinhan.heybob.domain.meal.controller;

import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentIdResponse;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentListResponse;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentStatisticsResponse;
import com.shinhan.heybob.domain.meal.entity.MealType;
import com.shinhan.heybob.domain.notification.service.ChatIntegrationService;
import com.shinhan.heybob.domain.meal.service.MealAppointmentService;
import com.sun.security.auth.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/meal-appointments")  // context-path가 /api이므로 /meal-appointments만 사용
@RequiredArgsConstructor
@Slf4j
public class MealAppointmentController {

    private final MealAppointmentService mealAppointmentService;

    @PostMapping
    public ResponseEntity<MealAppointmentIdResponse> createMealAppointment(
            @RequestBody @Valid CreateMealAppointmentRequest request,
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal) {
        try {
            log.info("🍚 밥약 생성 요청: name={}, participantIds={}, userPrincipal={}", 
                request.getName(), request.getParticipantIds(), userPrincipal != null ? userPrincipal.getUserId() : "null");
            
            // 인증된 사용자 ID를 creatorId로 설정
            if (userPrincipal != null) {
                request.setCreatorId(userPrincipal.getUserId());
            }
            
            MealAppointmentDetailResponse response = mealAppointmentService.createMealAppointment(request);
            MealAppointmentIdResponse idResponse = MealAppointmentIdResponse.builder()
                    .id(response.getId())
                    .build();
            
            log.info("✅ 밥약 생성 성공: id={}, chatRoomId={}", response.getId(), response.getChatRoomId());
            return ResponseEntity.status(HttpStatus.CREATED).body(idResponse);
            
        } catch (HeybobException e) {
            log.error("❌ 밥약 생성 실패 (HeybobException): {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("❌ 밥약 생성 실패 (예상치 못한 오류): {}", e.getMessage(), e);
            throw new HeybobException(ExceptionStatus.CHAT_INTEGRATION_FAILED);
        }
    }

    @GetMapping("/{appointmentId}")
    public ResponseEntity<MealAppointmentDetailResponse> getMealAppointment(
            @PathVariable Long appointmentId) {
        MealAppointmentDetailResponse response = mealAppointmentService.getMealAppointment(appointmentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<MealAppointmentDetailResponse>> getUserMealAppointments(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @RequestParam(defaultValue = "all") String type) {
        MealType mealType = "all".equalsIgnoreCase(type) ? null : MealType.valueOf(type.toUpperCase());
        List<MealAppointmentDetailResponse> response = mealAppointmentService.getUserMealAppointments(
                userPrincipal.getUserId(), mealType);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/list")
    public ResponseEntity<List<MealAppointmentListResponse>> getUserMealAppointmentList(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "all") String type) {
        MealType mealType = "all".equalsIgnoreCase(type) ? null : MealType.valueOf(type.toUpperCase());
        List<MealAppointmentListResponse> response = mealAppointmentService.getUserMealAppointmentList(
                userPrincipal.getUserId(), status, mealType);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistics")
    public ResponseEntity<MealAppointmentStatisticsResponse> getUserMealAppointmentStatistics(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipalDetails
    ) {
        MealAppointmentStatisticsResponse response = mealAppointmentService.getUserMealAppointmentStatistics(
                userPrincipalDetails.getUserId());
        return ResponseEntity.ok(response);
    }

}