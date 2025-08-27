package com.shinhan.heybob.domain.meal.dto.response;

import com.shinhan.heybob.domain.meal.entity.MealType;
import com.shinhan.heybob.domain.user.dto.UserResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealAppointmentDetailResponse {

    private Long id;
    private String name;
    private String memo;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private UserResponseDto creator;
    private List<UserResponseDto> participants;
    private String status;
    private MealType mealType;
    private Long chatRoomId;
    private LocalDateTime createdAt;
}