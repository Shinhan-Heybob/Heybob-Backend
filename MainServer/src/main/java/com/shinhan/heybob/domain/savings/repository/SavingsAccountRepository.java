package com.shinhan.heybob.domain.savings.repository;

import com.shinhan.heybob.domain.savings.entity.SavingsAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, Long> {

    Optional<SavingsAccount> findByMealAppointment_Id(Long mealAppointmentId);
}
