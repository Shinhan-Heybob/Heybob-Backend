package com.shinhan.heybob.domain.test.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementBroadcastRequest {
    private String settlementId;
    private String roomId;
    private String requesterName;   // 정산 요청자 이름
    private Integer requestAmount;  // 요청 금액
    private String message;         // 추가 메시지
}