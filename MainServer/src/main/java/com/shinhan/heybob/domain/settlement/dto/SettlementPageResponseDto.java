package com.shinhan.heybob.domain.settlement.dto;

import com.shinhan.heybob.domain.settlement.model.SettlementStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record SettlementPageResponseDto(
        Long settlementId,
        SettlementStatus status,

        // 헤더
        Long initiatorId,
        String initiatorName,

        // 모임(밥약) 정보
        Long mealAppointmentId,
        String mealName,
        LocalDate appointmentDate,
        LocalTime appointmentTime,

        // 금액/집계
        int totalAmount,
        int perHeadAmount,
        int participantsCount,
        int paidCount,

        // 참가자 목록
        List<SettlementParticipantItemDto> participants
) {}
