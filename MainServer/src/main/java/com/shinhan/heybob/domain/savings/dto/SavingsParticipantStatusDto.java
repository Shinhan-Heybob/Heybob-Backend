package com.shinhan.heybob.domain.savings.dto;

public record SavingsParticipantStatusDto (
        Long userId,
        String userName,
        boolean paid
){}
