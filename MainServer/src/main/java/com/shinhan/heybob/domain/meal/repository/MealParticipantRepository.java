package com.shinhan.heybob.domain.meal.repository;

import com.shinhan.heybob.domain.meal.entity.MealParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MealParticipantRepository extends JpaRepository<MealParticipant, Long> {
    
    List<MealParticipant> findByMealAppointmentId(Long mealAppointmentId);
    
    boolean existsByMealAppointmentIdAndUserId(Long mealAppointmentId, Long userId);
}