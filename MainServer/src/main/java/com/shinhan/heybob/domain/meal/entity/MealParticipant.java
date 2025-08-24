package com.shinhan.heybob.domain.meal.entity;

import com.shinhan.heybob.common.entity.BaseTime;
import com.shinhan.heybob.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "meal_participants",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"meal_appointment_id", "user_id"})
    })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealParticipant extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meal_participant_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_appointment_id", nullable = false)
    private MealAppointment mealAppointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected void setMealAppointment(MealAppointment mealAppointment) {
        this.mealAppointment = mealAppointment;
    }
}