package com.shinhan.heybob.domain.meal.service;

import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.request.ScheduleComparisonRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;
import com.shinhan.heybob.domain.meal.dto.response.ScheduleComparisonResponse;

import java.util.List;

public interface MealAppointmentService {

    ScheduleComparisonResponse compareSchedules(ScheduleComparisonRequest request);

    MealAppointmentDetailResponse createMealAppointment(CreateMealAppointmentRequest request);

    MealAppointmentDetailResponse getMealAppointment(Long appointmentId);

    List<MealAppointmentDetailResponse> getUserMealAppointments(Long userId);
}