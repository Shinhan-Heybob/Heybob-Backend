package com.shinhan.heybob.domain.meal.controller;

import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.request.ScheduleComparisonRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;
import com.shinhan.heybob.domain.meal.dto.response.ScheduleComparisonResponse;
import com.shinhan.heybob.domain.meal.service.MealAppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meal-appointments")
@RequiredArgsConstructor
public class MealAppointmentController {

    private final MealAppointmentService mealAppointmentService;

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
}