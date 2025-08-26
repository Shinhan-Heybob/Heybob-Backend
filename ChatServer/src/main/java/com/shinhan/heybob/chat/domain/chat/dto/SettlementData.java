package com.shinhan.heybob.chat.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated
public class SettlementData {
    private String settlementId;
    private String roomId;
    private String requesterName;   // 정산 요청자 이름
    private Integer requestAmount;  // 요청 금액  
    private String settlementUrl;   // Main 페이지 송금 URL
    
    // PaymentRequestData로부터 생성하는 팩토리 메서드
    public static SettlementData fromPaymentRequestData(PaymentRequestData paymentRequestData) {
        if (paymentRequestData == null) {
            return null;
        }
        return SettlementData.builder()
                .settlementId(paymentRequestData.getSettlementId())
                .roomId(paymentRequestData.getRoomId())
                .requesterName(paymentRequestData.getRequesterName())
                .requestAmount(paymentRequestData.getRequestAmount())
                .settlementUrl(paymentRequestData.getSettlementUrl())
                .build();
    }
    
    // PaymentRequestData로 변환하는 메서드
    public PaymentRequestData toPaymentRequestData() {
        return PaymentRequestData.builder()
                .settlementId(this.settlementId)
                .roomId(this.roomId)
                .requesterName(this.requesterName)
                .requestAmount(this.requestAmount)
                .settlementUrl(this.settlementUrl)
                .build();
    }
}