package com.shinhan.heybob.domain.meal.dto.request;

import jakarta.validation.constraints.NotNull;
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
public class ScheduleComparisonRequest {

    @NotNull(message = "날짜는 필수입니다.")
    private LocalDate date;

    @NotNull(message = "참여자 목록은 필수입니다.")
    private List<Long> participantIds;
}