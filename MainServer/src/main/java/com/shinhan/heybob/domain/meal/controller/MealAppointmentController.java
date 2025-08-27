package com.shinhan.heybob.domain.meal.controller;

import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentIdResponse;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentListResponse;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentStatisticsResponse;
import com.shinhan.heybob.domain.meal.entity.MealType;
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
            @RequestParam Long userId,
            @RequestParam(defaultValue = "all") String type) {
        MealType mealType = "all".equalsIgnoreCase(type) ? null : MealType.valueOf(type.toUpperCase());
        List<MealAppointmentDetailResponse> response = mealAppointmentService.getUserMealAppointments(userId, mealType);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/list")
    public ResponseEntity<List<MealAppointmentListResponse>> getUserMealAppointmentList(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "all") String type) {
        MealType mealType = "all".equalsIgnoreCase(type) ? null : MealType.valueOf(type.toUpperCase());
        List<MealAppointmentListResponse> response = mealAppointmentService.getUserMealAppointmentList(userId, status, mealType);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistics")
    public ResponseEntity<MealAppointmentStatisticsResponse> getUserMealAppointmentStatistics(
            @RequestParam Long userId) {
        MealAppointmentStatisticsResponse response = mealAppointmentService.getUserMealAppointmentStatistics(userId);
        return ResponseEntity.ok(response);
    }

}