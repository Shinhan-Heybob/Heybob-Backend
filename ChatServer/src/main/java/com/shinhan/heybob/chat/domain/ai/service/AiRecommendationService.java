package com.shinhan.heybob.chat.domain.ai.service;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;

public interface AiRecommendationService {
    
    /**
     * 사용자의 AI 봇 요청을 처리하고 추천을 생성
     * @param roomId 채팅방 ID
     * @param userId 사용자 ID
     * @param studentId 학번
     * @param userName 사용자 이름
     * @param profileImageUrl 프로필 이미지 URL
     * @param userQuery 사용자의 질문/요청
     * @return AI 봇 응답 메시지
     */
    ChatMessageResponse processAiRequest(String roomId, String userId, String studentId, 
                                        String userName, String profileImageUrl, String userQuery);
    
    /**
     * AI 봇 응답 생성 (비동기 처리용)
     * @param roomId 채팅방 ID
     * @param userQuery 사용자 질문
     */
    void generateAndSendAiResponse(String roomId, String userQuery);
}