package com.shinhan.heybob.domain.meal.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealAppointmentStatisticsResponse {

    private Long userId;
    private long mealAppointmentCount;
    private long regularMeetingCount;
    private long totalCount;
}