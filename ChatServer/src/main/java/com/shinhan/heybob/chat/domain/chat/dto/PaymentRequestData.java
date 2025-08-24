package com.shinhan.heybob.chat.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestData {
    private String settlementId;
    private String roomId;
    private String requesterName;   // 정산 요청자 이름
    private Integer requestAmount;  // 요청 금액  
    private String settlementUrl;   // Main 페이지 송금 URL
}