package com.shinhan.heybob.domain.timetable.dto;

import java.time.LocalDate;
import java.util.List;

public record TimetableCompareGetRequestDto(
        List<Long> userIdList,
        LocalDate day
) {
}
