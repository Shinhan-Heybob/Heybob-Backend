package com.shinhan.heybob.domain.meal.service;

import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentListResponse;
import com.shinhan.heybob.domain.meal.entity.MealType;

import java.util.List;

public interface MealAppointmentService {


    MealAppointmentDetailResponse createMealAppointment(CreateMealAppointmentRequest request);

    MealAppointmentDetailResponse getMealAppointment(Long appointmentId);

    List<MealAppointmentDetailResponse> getUserMealAppointments(Long userId);
    
    List<MealAppointmentDetailResponse> getUserMealAppointments(Long userId, MealType type);

    List<MealAppointmentListResponse> getUserMealAppointmentList(Long userId);

    List<MealAppointmentListResponse> getUserMealAppointmentList(Long userId, String status);
    
    List<MealAppointmentListResponse> getUserMealAppointmentList(Long userId, String status, MealType type);
}