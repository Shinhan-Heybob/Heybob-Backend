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
    
    // 요청자 정보
    private Long requesterId;           // 사용자 ID
    private String requesterName;       // 정산 요청자 이름
    private String requesterStudentId;  // 학번
    private String requesterProfileImg; // 프로필 이미지 URL
    
    private Integer requestAmount;  // 요청 금액  
    private String settlementUrl;   // Main 페이지 송금 URL
}