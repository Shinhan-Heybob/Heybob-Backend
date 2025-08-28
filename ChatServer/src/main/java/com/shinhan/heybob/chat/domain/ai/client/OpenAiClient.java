package com.shinhan.heybob.chat.domain.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiClient {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${openai.api.key}")
    private String apiKey;
    
    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;
    
    @Value("${openai.model:gpt-3.5-turbo}")
    private String model;
    
    @Value("${openai.max-tokens:1000}")
    private int maxTokens;
    
    @Value("${openai.temperature:0.7}")
    private double temperature;
    
    public String getMenuRecommendation(String userQuery, String cafeteriaMenu) {
        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(userQuery, cafeteriaMenu);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ));
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", temperature);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.info("OpenAI API 호출 시작: model={}, query length={}", model, userQuery.length());
            
            Map<String, Object> response = restTemplate.postForObject(apiUrl, request, Map.class);
            
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
                    String content = (String) message.get("content");
                    log.info("OpenAI API 응답 수신 성공");
                    return content;
                }
            }
            
            log.warn("OpenAI API 응답이 비어있습니다");
            return "죄송합니다. 메뉴 추천을 생성하는데 실패했습니다.";
            
        } catch (Exception e) {
            log.error("OpenAI API 호출 실패", e);
            return "AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해주세요.";
        }
    }
    
    private String buildSystemPrompt() {
        return """
            당신은 대학생들을 위한 친근한 메뉴 추천 AI 봇입니다.
            학생 식당의 오늘 메뉴를 고려하여, 겹치지 않는 외부 메뉴를 추천해야 합니다.
            
            다음 규칙을 따라주세요:
            1. 학식과 비슷한 메뉴나 재료가 겹치는 메뉴는 추천하지 마세요
            2. 학생들이 선호하고 접근하기 쉬운 메뉴를 추천하세요
            3. 가격대는 5,000원~15,000원 사이로 제안하세요
            4. 배달 가능하거나 학교 근처에서 쉽게 찾을 수 있는 메뉴를 추천하세요
            5. 친근하고 이모티콘을 활용한 톤으로 대답하세요
            6. 추천 이유를 간단히 설명해주세요
            7. 영양 균형도 고려해주세요
            """;
    }
    
    private String buildUserPrompt(String userQuery, String cafeteriaMenu) {
        return String.format("""
            오늘의 학식 메뉴:
            %s
            
            사용자 요청: %s
            
            위 학식 메뉴와 겹치지 않으면서 사용자의 요청을 반영한 메뉴 1가지를 추천해주세요.
            메뉴마다 추천 이유를 말해주세요. 단 간단하게 요약해주세요.
            """, cafeteriaMenu, userQuery);
    }
}