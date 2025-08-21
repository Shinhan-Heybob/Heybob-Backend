package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatStreamServiceImpl implements ChatStreamService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Override
    public void saveToStream(ChatMessageResponse message) {
        String streamKey = "room:messages:" + message.getRoomId();
        
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("messageId", message.getMessageId());
        messageData.put("senderId", message.getSenderId());
        messageData.put("studentId", message.getStudentId());
        messageData.put("senderName", message.getSenderName());
        messageData.put("profileImageUrl", message.getProfileImageUrl());
        messageData.put("content", message.getContent());
        messageData.put("messageType", message.getMessageType());
        messageData.put("timestamp", message.getTimestamp().toString());
        
        redisTemplate.opsForStream().add(streamKey, messageData);
        log.info("Redis Stream 저장 완료: roomId={}, messageId={}", message.getRoomId(), message.getMessageId());
    }
}