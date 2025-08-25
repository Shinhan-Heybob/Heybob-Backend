package com.shinhan.heybob.domain.settlement.repository;

import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByMealAppointment(MealAppointment mealAppointment);

    @Query("""
        select s
          from Settlement s
          join fetch s.initiator i
          join fetch s.mealAppointment m
          left join fetch s.participants sp
          left join fetch sp.participantUser u
         where s.id = :id
    """)
    Optional<Settlement> findDetailById(Long id);

    @Query("""
        select s
          from Settlement s
          join fetch s.initiator i
          join fetch s.mealAppointment m
          left join fetch s.participants sp
          left join fetch sp.participantUser u
         where m.chatRoomId = :chatRoomId
    """)
    Optional<Settlement> findDetailByChatRoomId(Long chatRoomId);

}

