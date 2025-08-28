package com.shinhan.heybob.domain.meal.dto.request;

import com.shinhan.heybob.domain.meal.entity.MealType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMealAppointmentRequest {

    @NotBlank(message = "밥약 이름은 필수입니다.")
    @Size(max = 100, message = "밥약 이름은 100자 이내여야 합니다.")
    private String name;

    private String memo;

    @NotNull(message = "약속 날짜는 필수입니다.")
    private LocalDate appointmentDate;

    @NotNull(message = "약속 시간은 필수입니다.")
    private LocalTime appointmentTime;

    @NotNull(message = "참여자 목록은 필수입니다.")
    private List<Long> participantIds;

    private Long creatorId;
    
    @Builder.Default
    private MealType mealType = MealType.MEAL_APPOINTMENT;
}