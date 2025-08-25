package com.shinhan.heybob.domain.notification.dto;

import com.shinhan.heybob.domain.notification.model.NotificationEventType;

public record ChatEventMessageDto(
        NotificationEventType type,
        Long chatRoomId,
        Long settlementId,
        Long initiatorId,
        String initiatorName,
        String title,        // "이지민님이 정산하기를 요청했습니다!"
        String ctaLabel      // "30,450원 송금하기"
) {}
