package com.shinhan.heybob.chat.domain.communication.handler;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinancialMessageFailureEvent {
    private String messageId;
    private String roomId;
    private String messageType; // PAYMENT_REQUEST, SAVINGS_REQUEST 등
    private String failureReason;
    private int attemptCount;
    private LocalDateTime failureTime;
    private Map<String, Object> messageData; // 복구를 위한 원본 데이터
    private boolean isBackedUpToRedis; // Redis Stream에 백업됐는지 여부
}