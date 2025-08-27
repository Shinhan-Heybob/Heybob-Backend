package com.shinhan.heybob.chat.domain.chat.handler.impl;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.handler.AbstractMessageHandler;
import com.shinhan.heybob.chat.domain.chat.handler.MessageContext;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatMessageHandler extends AbstractMessageHandler {
    
    @Override
    public boolean supports(ChatMessage.MessageType messageType) {
        return messageType == ChatMessage.MessageType.CHAT;
    }
    
    @Override
    public ChatMessageResponse handle(MessageContext context) {
        log.debug("일반 채팅 메시지 처리: roomId={}, sender={}", 
                context.getRoomId(), context.getUserName());
        
        return createBaseResponse(context).build();
    }
    
    @Override
    protected void validateSpecific(ChatMessageRequest request) {
    }
    
    @Override
    public boolean isFinancialMessage() {
        return false;
    }
}