package com.shinhan.heybob.chat.domain.chat.dto;

import lombok.Data;

@Data
public class ChatMessageRequest {
    private String roomId;
    private String content;
    private String messageType = "CHAT";  // CHAT, JOIN, LEAVE 등
    private SettlementData settlementData;  // 정산 요청 시 포함되는 데이터
    private String settlementId;  // 정산 응답 시 사용할 ID
}