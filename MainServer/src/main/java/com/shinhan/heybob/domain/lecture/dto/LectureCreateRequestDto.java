package com.shinhan.heybob.domain.lecture.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Builder;

@Builder
public record LectureCreateRequestDto(
        String name,

        String subjectCode,

        String dayOfWeek,

        LocalTime startTime,

        LocalTime endTime,

        String classroom,

        String professor
) {
}
