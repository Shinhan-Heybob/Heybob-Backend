package com.shinhan.heybob.domain.timetable.dto;

import com.shinhan.heybob.domain.user.entity.User;
import java.time.LocalTime;
import java.util.List;

public record TimeslotDto(
        LocalTime startTime,

        LocalTime endTime,

        List<User> availablePeople
) {
}
