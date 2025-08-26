package com.shinhan.heybob.domain.timetable.dto;

import java.util.List;

public record TimetableCompareGetRequestDto(
        List<Long> userIdList
) {
}
