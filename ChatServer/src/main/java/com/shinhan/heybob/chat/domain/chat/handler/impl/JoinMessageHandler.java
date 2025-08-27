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
public class JoinMessageHandler extends AbstractMessageHandler {
    
    @Override
    public boolean supports(ChatMessage.MessageType messageType) {
        return messageType == ChatMessage.MessageType.JOIN;
    }
    
    @Override
    public ChatMessageResponse handle(MessageContext context) {
        log.info("사용자 입장: roomId={}, user={}", context.getRoomId(), context.getUserName());
        
        String joinMessage = context.getUserName() + "님이 입장하셨습니다.";
        
        return createBaseResponse(context)
                .content(joinMessage)
                .build();
    }
    
    @Override
    protected void validateSpecific(ChatMessageRequest request) {
    }
    
    @Override
    public boolean isFinancialMessage() {
        return false;
    }
}