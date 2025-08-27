package com.shinhan.heybob.domain.settlement.entity;

import com.shinhan.heybob.common.entity.BaseTime;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.settlement.model.SettlementStatus;
import com.shinhan.heybob.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "settlement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Settlement extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_appointment_id", nullable = false)
    private MealAppointment mealAppointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_user_id", nullable = false)
    private User initiator;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Column(name = "per_head_amount", nullable = false)
    private int perHeadAmount;

    @Column(name = "participants_count", nullable = false)
    private int participantsCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SettlementStatus status = SettlementStatus.CREATED;

    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SettlementParticipant> participants = new ArrayList<>();

    public void markInProgress() { this.status = SettlementStatus.IN_PROGRESS; }
    public void markCompleted()  { this.status = SettlementStatus.COMPLETED; }
    public void cancel()         { this.status = SettlementStatus.CANCELED; }
}
