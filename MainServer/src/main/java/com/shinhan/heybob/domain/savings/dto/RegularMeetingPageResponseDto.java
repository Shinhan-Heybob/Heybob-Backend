package com.shinhan.heybob.domain.savings.dto;

import com.shinhan.heybob.domain.savings.entity.SavingsPlan;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record RegularMeetingPageResponseDto(
        // 모임 기본 정보
        Long mealAppointmentId,
        String meetingName,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        Long creatorId,
        String creatorName,
        
        // 적금 계좌 정보
        Long savingsAccountId,
        String accountNo,
        
        // 플랜 정보
        Long planId,
        int perHeadBalance,
        int currentCycle,
        int totalCycles,
        SavingsPlan.PlanStatus planStatus,
        
        // 집계 정보
        int totalSavedAmount,
        int participantsCount,
        
        // 회차별 저금 현황
        List<CycleStatusDto> cycles
) {}