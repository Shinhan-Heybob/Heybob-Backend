package com.shinhan.heybob.domain.savings;

import com.shinhan.heybob.common.entity.BaseTime;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_meal_owner", columnNames = {"meal_appointment_id","owner_user_id"})
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsAccount extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_no")
    private String accountNo;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_appointment_id", nullable = false)
    private MealAppointment mealAppointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_user_id", nullable = false)
    private User ownerUser;

}
