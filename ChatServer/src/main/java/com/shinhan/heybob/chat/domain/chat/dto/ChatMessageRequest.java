package com.shinhan.heybob.chat.domain.chat.dto;

import lombok.Data;

@Data
public class ChatMessageRequest {
    private String roomId;
    private String content;
    private String messageType = "CHAT";  // CHAT, JOIN, LEAVE 등
    private PaymentRequestData paymentRequestData;   // 결제 요청 데이터 (PAYMENT_REQUEST 타입에서 사용)
    private PaymentCompleteData paymentCompleteData; // 결제 완료 데이터 (PAYMENT_COMPLETE 타입에서 사용)
}