package com.shinhan.heybob.chat.domain.chat.dto;

import lombok.Data;

@Data
public class ChatMessageRequest {
    private String roomId;
    private String content;
    private String messageType = "CHAT";  // CHAT, CAFETERIA_INFO 등 (사용자가 직접 보낼 수 있는 타입만)
}