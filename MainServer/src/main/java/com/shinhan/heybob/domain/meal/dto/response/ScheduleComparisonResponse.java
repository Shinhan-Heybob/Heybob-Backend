package com.shinhan.heybob.domain.meal.dto.response;

import com.shinhan.heybob.domain.user.dto.UserResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleComparisonResponse {

    private LocalDate date;
    private List<UserResponseDto> participants;
    private List<TimeSlotDto> availableSlots;
}