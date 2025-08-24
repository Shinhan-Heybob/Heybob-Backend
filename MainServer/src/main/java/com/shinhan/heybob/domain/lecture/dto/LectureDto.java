package com.shinhan.heybob.domain.lecture.dto;

import java.time.LocalTime;
import lombok.Builder;

@Builder
public record LectureDto(
        Long lectureId,

        String lectureName,

        String subjectCode,

        String dayOfWeek,

        LocalTime startTime,

        LocalTime endTime,

        String classroom,

        String professor
) {
}
