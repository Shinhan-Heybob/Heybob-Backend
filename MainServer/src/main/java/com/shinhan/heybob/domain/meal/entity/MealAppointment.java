package com.shinhan.heybob.domain.meal.entity;

import com.shinhan.heybob.common.entity.BaseTime;
import com.shinhan.heybob.domain.settlement.entity.Settlement;
import com.shinhan.heybob.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "meal_appointments")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealAppointment extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meal_appointment_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "appointment_time", nullable = false)
    private LocalTime appointmentTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private MealAppointmentStatus status = MealAppointmentStatus.ACTIVE;

    @OneToMany(mappedBy = "mealAppointment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MealParticipant> participants = new ArrayList<>();

    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private Settlement settlement;


    public void updateStatus(MealAppointmentStatus status) {
        this.status = status;
    }

    public void addParticipant(MealParticipant participant) {
        this.participants.add(participant);
        participant.setMealAppointment(this);
    }
}