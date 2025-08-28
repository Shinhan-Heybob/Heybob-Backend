package com.shinhan.heybob.chat.domain.ai.service;

import com.shinhan.heybob.chat.domain.ai.client.OpenAiClient;
import com.shinhan.heybob.chat.domain.cafeteria.service.CafeteriaService;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecommendationServiceImpl implements AiRecommendationService {
    
    private final OpenAiClient openAiClient;
    private final CafeteriaService cafeteriaService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    
    private static final String AI_BOT_ID = "ai_bot";
    private static final String AI_BOT_NAME = "AI 메뉴 추천봇";
    private static final String AI_BOT_PROFILE = "https://example.com/ai-bot-profile.png";
    
    @Override
    public ChatMessageResponse processAiRequest(String roomId, String userId, String studentId, 
                                               String userName, String profileImageUrl, String userQuery) {
        try {
            log.info("AI 봇 요청 처리 시작: roomId={}, userId={}, query={}", roomId, userId, userQuery);
            
            // 1. 사용자 메시지 생성 (AI_BOT_REQUEST 타입)
            String messageId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            
            ChatMessage userMessage = ChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .senderId(userId)
                .studentId(studentId)
                .senderName(userName)
                .profileImageUrl(profileImageUrl)
                .content(userQuery)
                .messageType(ChatMessage.MessageType.AI_BOT_REQUEST)
                .timestamp(now)
                .emergencyFallback(false)
                .build();
            
            // 2. 사용자 메시지 저장
            chatService.saveMessage(userMessage);
            
            // 3. 사용자 메시지 응답 생성
            ChatMessageResponse userResponse = ChatMessageResponse.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(userId)
                .studentId(studentId)
                .senderName(userName)
                .profileImageUrl(profileImageUrl)
                .content(userQuery)
                .messageType("AI_BOT_REQUEST")
                .timestamp(now)
                .build();
            
            // 4. AI 응답 생성을 비동기로 처리
            generateAndSendAiResponse(roomId, userQuery);
            
            log.info("AI 봇 요청 메시지 처리 완료: messageId={}", messageId);
            return userResponse;
            
        } catch (Exception e) {
            log.error("AI 봇 요청 처리 실패: roomId={}, userId={}", roomId, userId, e);
            throw new RuntimeException("AI 봇 요청 처리 중 오류가 발생했습니다.", e);
        }
    }
    
    @Override
    @Async
    public void generateAndSendAiResponse(String roomId, String userQuery) {
        try {
            log.info("AI 응답 생성 시작: roomId={}, query={}", roomId, userQuery);
            
            // 1. 학식 정보 조회
            String cafeteriaInfo = cafeteriaService.getTodayCafeteriaInfo();
            log.info("학식 정보 조회 완료");
            
            // 2. OpenAI API 호출
            String aiRecommendation = openAiClient.getMenuRecommendation(userQuery, cafeteriaInfo);
            log.info("OpenAI API 응답 수신 완료");
            
            // 3. AI 봇 응답 메시지 생성
            String botMessageId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            
            // 응답 포맷팅
            String formattedResponse = formatAiResponse(aiRecommendation, cafeteriaInfo);
            
            ChatMessage botMessage = ChatMessage.builder()
                .id(botMessageId)
                .roomId(roomId)
                .senderId(AI_BOT_ID)
                .studentId(AI_BOT_ID)
                .senderName(AI_BOT_NAME)
                .profileImageUrl(AI_BOT_PROFILE)
                .content(formattedResponse)
                .messageType(ChatMessage.MessageType.AI_BOT_RESPONSE)
                .timestamp(now)
                .emergencyFallback(false)
                .build();
            
            // 4. AI 봇 메시지 저장
            chatService.saveMessage(botMessage);
            
            // 5. AI 봇 응답 브로드캐스트
            ChatMessageResponse botResponse = ChatMessageResponse.builder()
                .messageId(botMessageId)
                .roomId(roomId)
                .senderId(AI_BOT_ID)
                .studentId(AI_BOT_ID)
                .senderName(AI_BOT_NAME)
                .profileImageUrl(AI_BOT_PROFILE)
                .content(formattedResponse)
                .messageType("AI_BOT_RESPONSE")
                .timestamp(now)
                .build();
            
            messagingTemplate.convertAndSend("/topic/room/" + roomId, botResponse);
            log.info("AI 봇 응답 브로드캐스트 완료: roomId={}, messageId={}", roomId, botMessageId);
            
        } catch (Exception e) {
            log.error("AI 응답 생성 실패: roomId={}", roomId, e);
            
            // 에러 발생 시 사용자에게 에러 메시지 전송
            sendErrorMessage(roomId, "죄송합니다. AI 추천을 생성하는데 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
    }
    
    private String formatAiResponse(String aiRecommendation, String cafeteriaInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("🤖 AI 메뉴 추천\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n");
        
        // 학식 정보가 있는 경우 간단히 요약
        if (!cafeteriaInfo.contains("학식이 없습니다")) {
            sb.append("📍 오늘 학식 참고:\n");
            String[] lines = cafeteriaInfo.split("\n");
            int count = 0;
            for (String line : lines) {
                if (line.contains("중식") || line.contains("석식")) {
                    sb.append(line).append("\n");
                    count++;
                    if (count >= 2) break; // 중식, 석식 정보만 간단히 표시
                }
            }
            sb.append("\n");
        }
        
        sb.append("💡 추천 메뉴:\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append(aiRecommendation);
        
        return sb.toString();
    }
    
    private void sendErrorMessage(String roomId, String errorMessage) {
        try {
            String messageId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            
            ChatMessageResponse errorResponse = ChatMessageResponse.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(AI_BOT_ID)
                .studentId(AI_BOT_ID)
                .senderName(AI_BOT_NAME)
                .profileImageUrl(AI_BOT_PROFILE)
                .content(errorMessage)
                .messageType("AI_BOT_RESPONSE")
                .timestamp(now)
                .build();
            
            messagingTemplate.convertAndSend("/topic/room/" + roomId, errorResponse);
            
        } catch (Exception e) {
            log.error("에러 메시지 전송 실패: roomId={}", roomId, e);
        }
    }
}