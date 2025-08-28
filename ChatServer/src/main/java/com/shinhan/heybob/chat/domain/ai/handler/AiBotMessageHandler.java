package com.shinhan.heybob.chat.domain.ai.handler;

import com.shinhan.heybob.chat.domain.ai.service.AiRecommendationService;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.handler.AbstractMessageHandler;
import com.shinhan.heybob.chat.domain.chat.handler.MessageContext;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiBotMessageHandler extends AbstractMessageHandler {
    
    private final AiRecommendationService aiRecommendationService;
    
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("AiBotMessageHandler 초기화 완료. AI 서비스 연결: {}", 
                aiRecommendationService != null ? "성공" : "실패");
    }
    
    @Override
    public boolean supports(ChatMessage.MessageType messageType) {
        return messageType == ChatMessage.MessageType.AI_BOT_REQUEST;
    }
    
    @Override
    public ChatMessageResponse handle(MessageContext context) {
        log.info("AI 봇 메시지 처리: roomId={}, sender={}, content={}", 
                context.getRoomId(), context.getUserName(), context.getRequest().getContent());
        
        // AI 추천 서비스 호출
        ChatMessageResponse response = aiRecommendationService.processAiRequest(
            context.getRoomId(),
            context.getUserId(),
            context.getStudentId(),
            context.getUserName(),
            context.getProfileImageUrl(),
            context.getRequest().getContent()
        );
        
        return response;
    }
    
    @Override
    protected void validateSpecific(ChatMessageRequest request) {
        // AI 봇 요청에 특별한 검증이 필요한 경우 여기에 추가
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("AI 봇에게 질문할 내용을 입력해주세요.");
        }
        
        // 요청 내용 길이 제한
        if (request.getContent().length() > 500) {
            throw new IllegalArgumentException("질문이 너무 깁니다. 500자 이내로 입력해주세요.");
        }
    }
    
    @Override
    public boolean isFinancialMessage() {
        return false; // AI 봇 메시지는 금융 메시지가 아님
    }
}