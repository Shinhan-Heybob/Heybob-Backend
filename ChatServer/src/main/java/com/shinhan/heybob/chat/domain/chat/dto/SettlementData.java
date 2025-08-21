package com.shinhan.heybob.chat.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementData {
    private String settlementId;
    private String roomId;  // 스케줄러에서 브로드캐스트 시 필요
    private String note;
    private Integer totalAmount;
    private Integer perPersonAmount;
    private List<String> participants;
    private LocalDateTime expiryTime;
    private Map<String, SettlementStatus> participantStatus;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementStatus {
        private String status;  // "pending", "accepted", "rejected"
        private LocalDateTime responseTime;
    }
}