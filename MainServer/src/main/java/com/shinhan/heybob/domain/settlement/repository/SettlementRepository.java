package com.shinhan.heybob.domain.settlement.repository;

import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByMealAppointment(MealAppointment mealAppointment);

}

