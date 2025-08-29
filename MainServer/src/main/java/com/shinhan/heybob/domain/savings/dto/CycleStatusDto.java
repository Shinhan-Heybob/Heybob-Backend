package com.shinhan.heybob.domain.savings.dto;

import java.util.List;

public record CycleStatusDto(
        int cycleNo,
        int expectedAmount,
        int actualAmount,
        int participantsCount,
        int paidCount,
        List<CycleParticipantDto> participants
) {}