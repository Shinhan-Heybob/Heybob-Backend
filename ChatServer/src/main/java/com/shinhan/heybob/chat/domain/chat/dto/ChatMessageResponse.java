package com.shinhan.heybob.chat.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
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
    private PaymentRequestData paymentRequestData;  // 결제 요청 데이터
    private PaymentCompleteData paymentCompleteData; // 결제 완료 데이터
    private UiState uiState;  // UI 상태 정보
}