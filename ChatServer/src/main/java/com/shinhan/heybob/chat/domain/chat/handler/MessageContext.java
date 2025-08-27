package com.shinhan.heybob.chat.domain.chat.handler;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageContext {
    private String roomId;
    private String userId;
    private String studentId;
    private String userName;
    private String profileImageUrl;
    private ChatMessageRequest request;
}