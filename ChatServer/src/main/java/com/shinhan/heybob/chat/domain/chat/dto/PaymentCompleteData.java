package com.shinhan.heybob.chat.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompleteData {
    private String settlementId;
    private String roomId;
    private String recipientId;     // 개별 메시지 수신자 ID
    private String recipientName;   // 수신자 이름
    private Integer completedAmount; // 완료된 금액
}