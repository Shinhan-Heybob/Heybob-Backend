package com.shinhan.heybob.domain.meal.repository;

import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.entity.MealType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MealAppointmentRepository extends JpaRepository<MealAppointment, Long> {

    @Query("SELECT DISTINCT ma FROM MealAppointment ma " +
           "LEFT JOIN FETCH ma.participants p " +
           "LEFT JOIN FETCH ma.creator " +
           "WHERE ma.id = :id")
    Optional<MealAppointment> findByIdWithParticipants(@Param("id") Long id);

    @Query("SELECT DISTINCT ma FROM MealAppointment ma " +
           "LEFT JOIN FETCH ma.participants p " +
           "LEFT JOIN FETCH ma.creator " +
           "WHERE p.user.id = :userId OR ma.creator.id = :userId " +
           "ORDER BY ma.appointmentDate DESC, ma.appointmentTime DESC")
    List<MealAppointment> findByUserIdWithParticipants(@Param("userId") Long userId);

    @Query("SELECT ma FROM MealAppointment ma " +
           "WHERE ma.appointmentDate = :date " +
           "ORDER BY ma.appointmentTime")
    List<MealAppointment> findByAppointmentDate(@Param("date") LocalDate date);
    
    @Query("SELECT DISTINCT ma FROM MealAppointment ma " +
           "LEFT JOIN FETCH ma.participants p " +
           "LEFT JOIN FETCH ma.creator " +
           "WHERE (p.user.id = :userId OR ma.creator.id = :userId) " +
           "AND ma.type = :type " +
           "ORDER BY ma.appointmentDate DESC, ma.appointmentTime DESC")
    List<MealAppointment> findByUserIdAndTypeWithParticipants(@Param("userId") Long userId, @Param("type") MealType type);
    
    List<MealAppointment> findAllByCreatorId(Long userId);
    
    List<MealAppointment> findAllByCreatorIdAndType(Long userId, MealType type);
    
    @Query("SELECT COUNT(DISTINCT ma) FROM MealAppointment ma " +
           "LEFT JOIN ma.participants p " +
           "WHERE (p.user.id = :userId OR ma.creator.id = :userId) " +
           "AND ma.type = :type")
    long countByUserIdAndType(@Param("userId") Long userId, @Param("type") MealType type);
}