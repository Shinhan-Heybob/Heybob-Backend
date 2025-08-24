package com.shinhan.heybob.chat.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shinhan.heybob.chat.domain.chat.dto.ChatHistoryResponse;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.repository.ChatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Redis 인프라 연결이 필요한 통합 테스트")
class ChatHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatRepository chatRepository;

    private ObjectMapper objectMapper;
    private String roomId;
    private String userId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        userId = "integration-test-user";
    }

    @Test
    void 실제_데이터베이스_연동_히스토리_조회_테스트() throws Exception {
        // Given - 고유한 방 ID와 테스트 메시지 저장
        roomId = "integration-test-room-1";
        List<ChatMessage> testMessages = Arrays.asList(
                createTestMessage("msg001", "첫 번째 메시지", LocalDateTime.now().minusMinutes(30)),
                createTestMessage("msg002", "두 번째 메시지", LocalDateTime.now().minusMinutes(20)),
                createTestMessage("msg003", "세 번째 메시지", LocalDateTime.now().minusMinutes(10)),
                createTestMessage("msg004", "네 번째 메시지", LocalDateTime.now().minusMinutes(5)),
                createTestMessage("msg005", "다섯 번째 메시지", LocalDateTime.now())
        );
        
        chatRepository.saveAll(testMessages);

        // When - API 호출
        MvcResult result = mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .param("limit", "3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Then - 응답 검증
        String responseBody = result.getResponse().getContentAsString();
        ChatHistoryResponse response = objectMapper.readValue(responseBody, ChatHistoryResponse.class);

        assertNotNull(response);
        assertEquals(3, response.getTotalCount());
        assertTrue(response.isHasMore()); // 5개 중 3개만 조회했으므로 더 있음
        assertNotNull(response.getLastMessageId());
        
        // 메시지는 최신순으로 정렬되어야 함
        assertEquals(3, response.getMessages().size());
        assertTrue(response.getMessages().get(0).getTimestamp()
                .isAfter(response.getMessages().get(1).getTimestamp()));
    }

    @Test
    void 페이징_연속_조회_테스트() throws Exception {
        // Given - 고유한 방 ID와 10개의 테스트 메시지 저장
        roomId = "integration-test-room-2";
        for (int i = 1; i <= 10; i++) {
            ChatMessage message = createTestMessage(
                    "msg" + String.format("%03d", i),
                    "메시지 " + i,
                    LocalDateTime.now().minusMinutes(60 - i * 5)
            );
            chatRepository.save(message);
        }

        // When - 첫 번째 페이지 조회 (최신 3개)
        MvcResult firstResult = mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .param("limit", "3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        ChatHistoryResponse firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(), ChatHistoryResponse.class);

        // Then - 첫 번째 페이지 검증
        assertEquals(3, firstResponse.getTotalCount());
        assertTrue(firstResponse.isHasMore());
        String lastMessageId = firstResponse.getLastMessageId();
        assertNotNull(lastMessageId);

        // When - 두 번째 페이지 조회 (이전 3개)
        MvcResult secondResult = mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .param("before", lastMessageId)
                        .param("limit", "3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        ChatHistoryResponse secondResponse = objectMapper.readValue(
                secondResult.getResponse().getContentAsString(), ChatHistoryResponse.class);

        // Then - 두 번째 페이지 검증
        assertEquals(3, secondResponse.getTotalCount());
        assertTrue(secondResponse.isHasMore());
        
        // 두 페이지의 메시지가 겹치지 않아야 함
        String firstPageLastId = firstResponse.getLastMessageId();
        String secondPageFirstId = secondResponse.getMessages().get(0).getMessageId();
        assertNotEquals(firstPageLastId, secondPageFirstId);
    }

    @Test
    void 마지막_페이지_hasMore_false_테스트() throws Exception {
        // Given - 고유한 방 ID와 2개의 메시지만 저장
        roomId = "integration-test-room-3";
        List<ChatMessage> testMessages = Arrays.asList(
                createTestMessage("msg001", "첫 번째 메시지", LocalDateTime.now().minusMinutes(10)),
                createTestMessage("msg002", "두 번째 메시지", LocalDateTime.now())
        );
        
        chatRepository.saveAll(testMessages);

        // When - 5개 요청 (실제로는 2개만 있음)
        MvcResult result = mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .param("limit", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        ChatHistoryResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ChatHistoryResponse.class);

        assertEquals(2, response.getTotalCount());
        assertFalse(response.isHasMore()); // 요청한 것보다 적으므로 더 없음
        assertEquals("msg001", response.getLastMessageId()); // 가장 오래된 메시지가 마지막
    }

    @Test
    void 빈_방_히스토리_조회_테스트() throws Exception {
        // Given - 고유한 방 ID와 메시지가 없는 상태
        roomId = "integration-test-room-4";

        // When
        MvcResult result = mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        ChatHistoryResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ChatHistoryResponse.class);

        assertEquals(0, response.getTotalCount());
        assertFalse(response.isHasMore());
        assertNull(response.getLastMessageId());
        assertTrue(response.getMessages().isEmpty());
    }

    @Test
    void 다양한_메시지_타입_조회_테스트() throws Exception {
        // Given - 고유한 방 ID와 다양한 타입의 메시지 저장
        roomId = "integration-test-room-5";
        List<ChatMessage> diverseMessages = Arrays.asList(
                createTestMessageWithType("msg001", "일반 메시지", LocalDateTime.now().minusMinutes(30), ChatMessage.MessageType.CHAT),
                createTestMessageWithType("msg002", "입장 메시지", LocalDateTime.now().minusMinutes(20), ChatMessage.MessageType.JOIN),
                createTestMessageWithType("msg003", "퇴장 메시지", LocalDateTime.now().minusMinutes(10), ChatMessage.MessageType.LEAVE),
                createTestMessageWithType("msg004", "결제 요청", LocalDateTime.now(), ChatMessage.MessageType.PAYMENT_REQUEST)
        );
        
        chatRepository.saveAll(diverseMessages);

        // When
        MvcResult result = mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        ChatHistoryResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ChatHistoryResponse.class);

        assertEquals(4, response.getTotalCount());
        assertFalse(response.isHasMore());
        
        // 메시지 타입이 올바르게 반환되는지 확인
        List<String> messageTypes = response.getMessages().stream()
                .map(msg -> msg.getMessageType())
                .toList();
        
        assertTrue(messageTypes.contains("CHAT"));
        assertTrue(messageTypes.contains("JOIN"));
        assertTrue(messageTypes.contains("LEAVE"));
        assertTrue(messageTypes.contains("PAYMENT_REQUEST"));
    }

    @Test
    void 레거시_API_실제_동작_테스트() throws Exception {
        // Given - 고유한 방 ID
        roomId = "integration-test-room-6";
        List<ChatMessage> testMessages = Arrays.asList(
                createTestMessage("msg001", "레거시 테스트 1", LocalDateTime.now().minusMinutes(10)),
                createTestMessage("msg002", "레거시 테스트 2", LocalDateTime.now())
        );
        
        chatRepository.saveAll(testMessages);

        // When - 레거시 엔드포인트 호출
        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages/legacy", roomId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].messageId").exists())
                .andExpect(jsonPath("$[0].content").exists())
                .andExpect(jsonPath("$[1].messageId").exists())
                .andExpect(jsonPath("$[1].content").exists());
    }

    private ChatMessage createTestMessage(String id, String content, LocalDateTime timestamp) {
        return createTestMessageWithType(id, content, timestamp, ChatMessage.MessageType.CHAT);
    }

    private ChatMessage createTestMessageWithType(String id, String content, LocalDateTime timestamp, ChatMessage.MessageType messageType) {
        return ChatMessage.builder()
                .id(id)
                .roomId(roomId)
                .senderId(userId)
                .studentId("20230001")
                .senderName("통합테스트사용자")
                .profileImageUrl("https://example.com/profile.jpg")
                .content(content)
                .messageType(messageType)
                .timestamp(timestamp)
                .build();
    }
}