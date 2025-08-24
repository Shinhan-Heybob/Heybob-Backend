package com.shinhan.heybob.domain.meal.service;

import com.shinhan.heybob.domain.meal.entity.MealAppointment;

public interface ChatIntegrationService {
    
    Long createChatRoom(MealAppointment mealAppointment);
}