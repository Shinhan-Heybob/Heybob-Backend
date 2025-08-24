package com.shinhan.heybob.chat.domain.chat.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageResponse {
    private String messageId;
    private String roomId;
    private String senderId;
    private String studentId;
    private String senderName;
    private String profileImageUrl;
    private String content;
    private String messageType;
    private LocalDateTime timestamp;
    private SettlementData settlementData;  // 정산 관련 데이터
    private UiState uiState;  // UI 상태 정보
}