package com.shinhan.heybob.domain.meal.dto.response;

import com.shinhan.heybob.domain.meal.entity.MealType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealAppointmentListResponse {

    private Long id;
    private String name;
    private String creatorName;
    private String creatorStudentId;
    private String creatorDepartment;
    private Long chatRoomId;
    private MealType mealType;
    private boolean isActive;
}