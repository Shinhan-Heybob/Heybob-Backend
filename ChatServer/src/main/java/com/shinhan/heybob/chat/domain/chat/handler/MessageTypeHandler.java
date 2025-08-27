package com.shinhan.heybob.chat.domain.chat.handler;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;

public interface MessageTypeHandler {
    
    boolean supports(ChatMessage.MessageType messageType);
    
    ChatMessageResponse handle(MessageContext context);
    
    void validate(ChatMessageRequest request);
    
    boolean isFinancialMessage();
}