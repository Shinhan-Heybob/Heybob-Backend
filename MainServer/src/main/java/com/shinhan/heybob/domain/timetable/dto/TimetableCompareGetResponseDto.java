package com.shinhan.heybob.domain.timetable.dto;

import lombok.Builder;

import java.time.LocalTime;
import java.util.List;

@Builder
public record TimetableCompareGetResponseDto(
        List<TimeslotDto> timeslots
) {
}
