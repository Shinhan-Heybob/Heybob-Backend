package com.shinhan.heybob.common.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBroadcastRequest {
    private String settlementId;
    private String roomId;
    
    // 요청자 정보
    private Long requesterId;         // 사용자 ID
    private String requesterName;     // 사용자 이름
    private String requesterStudentId;// 학번
    private String requesterProfileImg;// 프로필 이미지 URL
    
    private Integer requestAmount;
    private String message;
    private BroadcastType type;
    
    public enum BroadcastType {
        PAYMENT,
        SAVINGS
    }
}