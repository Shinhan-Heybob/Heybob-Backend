package com.shinhan.heybob.domain.timetable.dto;

import com.shinhan.heybob.domain.lecture.dto.LectureDto;
import java.util.List;
import lombok.Builder;

@Builder
public record TimetableGetResponseDto(
        Long id,
        String timeTableName,
        List<LectureDto> lectures
) {
}
