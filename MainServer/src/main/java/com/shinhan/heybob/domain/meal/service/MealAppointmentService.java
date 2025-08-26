package com.shinhan.heybob.domain.meal.service;

import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;

import java.util.List;

public interface MealAppointmentService {


    MealAppointmentDetailResponse createMealAppointment(CreateMealAppointmentRequest request);

    MealAppointmentDetailResponse getMealAppointment(Long appointmentId);

    List<MealAppointmentDetailResponse> getUserMealAppointments(Long userId);
}