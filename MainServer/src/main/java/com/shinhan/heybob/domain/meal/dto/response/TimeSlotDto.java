package com.shinhan.heybob.domain.meal.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlotDto {

    private String time;
    private int availableCount;
    private List<String> availableUsers;
    private boolean isSelectable;
}