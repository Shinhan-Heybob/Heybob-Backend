package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.ChatHistoryResponse;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;

import java.util.List;

public interface ChatService {
    
    ChatMessageResponse processMessage(String roomId, String userId, String studentId, 
                                     String userName, String profileImageUrl, ChatMessageRequest request);
    
    List<ChatMessageResponse> getRecentMessages(String roomId, int limit);
    
    List<ChatMessageResponse> getMessagesBefore(String roomId, String beforeMessageId, int limit);
    
    // 새로운 히스토리 조회 메서드 (마지막 메시지 ID와 hasMore 포함)
    ChatHistoryResponse getChatHistory(String roomId, String beforeMessageId, int limit);
    
    // 메시지 직접 저장 (정산 메시지 등 특수 목적용)
    void saveMessage(ChatMessage chatMessage);
    
    // 학식 정보 처리
    ChatMessageResponse processCafeteriaInfo(String roomId, String userId, String studentId, 
                                           String userName, String profileImageUrl, String cafeteriaInfo);
}