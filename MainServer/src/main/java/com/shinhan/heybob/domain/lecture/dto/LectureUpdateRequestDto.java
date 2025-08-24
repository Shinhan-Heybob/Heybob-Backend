package com.shinhan.heybob.domain.lecture.dto;

import java.time.LocalTime;

public record LectureUpdateRequestDto(
        String name,

        String subjectCode,

        String dayOfWeek,

        LocalTime startTime,

        LocalTime endTime,

        String classroom,

        String professor
) {
}
