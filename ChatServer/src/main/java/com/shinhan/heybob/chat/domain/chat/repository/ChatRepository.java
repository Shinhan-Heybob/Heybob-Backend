package com.shinhan.heybob.chat.domain.chat.repository;

import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;

import java.util.List;

public interface ChatRepository {
    
    ChatMessage save(ChatMessage message);
    
    List<ChatMessage> saveAll(List<ChatMessage> messages);
    
    List<ChatMessage> findRecentMessagesByRoomId(String roomId, int limit);
    
    List<ChatMessage> findMessagesBeforeId(String roomId, String beforeMessageId, int limit);
}