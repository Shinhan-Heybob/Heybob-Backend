package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.handler.MessageContext;
import com.shinhan.heybob.chat.domain.chat.handler.MessageTypeHandler;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.global.error.ChatException;
import com.shinhan.heybob.chat.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDispatcher {
    
    private final List<MessageTypeHandler> handlers;
    private final Map<ChatMessage.MessageType, MessageTypeHandler> handlerMap = new HashMap<>();
    
    @PostConstruct
    public void init() {
        for (MessageTypeHandler handler : handlers) {
            for (ChatMessage.MessageType type : ChatMessage.MessageType.values()) {
                if (handler.supports(type)) {
                    handlerMap.put(type, handler);
                    log.info("핸들러 등록: {} -> {}", type, handler.getClass().getSimpleName());
                }
            }
        }
    }
    
    public ChatMessageResponse dispatch(String roomId, String userId, String studentId,
                                       String userName, String profileImageUrl, 
                                       ChatMessageRequest request) {
        
        validateRequest(roomId, request);
        
        ChatMessage.MessageType messageType;
        try {
            messageType = ChatMessage.MessageType.valueOf(request.getMessageType());
        } catch (IllegalArgumentException e) {
            log.error("지원하지 않는 메시지 타입: {}", request.getMessageType());
            throw new ChatException(ErrorCode.INVALID_MESSAGE_TYPE);
        }
        
        MessageTypeHandler handler = handlerMap.get(messageType);
        if (handler == null) {
            log.error("핸들러를 찾을 수 없음: messageType={}", messageType);
            throw new ChatException(ErrorCode.INVALID_MESSAGE_TYPE, 
                    "지원하지 않는 메시지 타입: " + messageType);
        }
        
        MessageContext context = MessageContext.builder()
                .roomId(roomId)
                .userId(userId)
                .studentId(studentId)
                .userName(userName)
                .profileImageUrl(profileImageUrl)
                .request(request)
                .build();
        
        handler.validate(request);
        
        ChatMessageResponse response = handler.handle(context);
        
        log.info("메시지 처리 완료: type={}, roomId={}, messageId={}", 
                messageType, roomId, response.getMessageId());
        
        return response;
    }
    
    private void validateRequest(String roomId, ChatMessageRequest request) {
        if (roomId == null || roomId.trim().isEmpty()) {
            throw new ChatException(ErrorCode.ROOM_NOT_FOUND);
        }
        
        if (request == null) {
            throw new ChatException(ErrorCode.INVALID_REQUEST);
        }
    }
    
    public boolean isFinancialMessage(String messageType) {
        try {
            ChatMessage.MessageType type = ChatMessage.MessageType.valueOf(messageType);
            MessageTypeHandler handler = handlerMap.get(type);
            return handler != null && handler.isFinancialMessage();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}