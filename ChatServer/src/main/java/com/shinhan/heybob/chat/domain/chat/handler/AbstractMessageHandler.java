package com.shinhan.heybob.chat.domain.chat.handler;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.global.error.ChatException;
import com.shinhan.heybob.chat.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
public abstract class AbstractMessageHandler implements MessageTypeHandler {
    
    @Override
    public void validate(ChatMessageRequest request) {
        if (request == null) {
            throw new ChatException(ErrorCode.INVALID_REQUEST);
        }
        
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new ChatException(ErrorCode.INVALID_REQUEST);
        }
        
        if (request.getMessageType() == null) {
            throw new ChatException(ErrorCode.INVALID_MESSAGE_TYPE);
        }
        
        validateSpecific(request);
    }
    
    protected abstract void validateSpecific(ChatMessageRequest request);
    
    protected ChatMessageResponse.ChatMessageResponseBuilder createBaseResponse(MessageContext context) {
        return ChatMessageResponse.builder()
                .messageId(UUID.randomUUID().toString())
                .roomId(context.getRoomId())
                .senderId(context.getUserId())
                .studentId(context.getStudentId())
                .senderName(context.getUserName())
                .profileImageUrl(context.getProfileImageUrl())
                .content(context.getRequest().getContent())
                .messageType(context.getRequest().getMessageType())
                .timestamp(LocalDateTime.now());
    }
}