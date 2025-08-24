package com.shinhan.heybob.chat.domain.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shinhan.heybob.chat.domain.chat.dto.ChatHistoryResponse;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ChatHistoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatHistoryController chatHistoryController;

    private ObjectMapper objectMapper;
    private String roomId;
    private String userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(chatHistoryController).build();
        
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        roomId = "test-room-001";
        userId = "test-user-001";
    }

    @Test
    void 초기_채팅_히스토리_조회_성공() throws Exception {
        // Given
        List<ChatMessageResponse> messages = Arrays.asList(
                createMockResponse("msg001", "첫 번째 메시지"),
                createMockResponse("msg002", "두 번째 메시지"),
                createMockResponse("msg003", "세 번째 메시지")
        );
        
        ChatHistoryResponse mockResponse = ChatHistoryResponse.builder()
                .messages(messages)
                .lastMessageId("msg003")
                .hasMore(true)
                .totalCount(3)
                .build();

        when(chatService.getChatHistory(roomId, null, 50))
                .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.messages.length()").value(3))
                .andExpect(jsonPath("$.messages[0].messageId").value("msg001"))
                .andExpect(jsonPath("$.messages[0].content").value("첫 번째 메시지"))
                .andExpect(jsonPath("$.messages[1].messageId").value("msg002"))
                .andExpect(jsonPath("$.messages[2].messageId").value("msg003"))
                .andExpect(jsonPath("$.lastMessageId").value("msg003"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.totalCount").value(3));
    }

    @Test
    void 특정_메시지_이전_히스토리_조회_성공() throws Exception {
        // Given
        String beforeMessageId = "msg003";
        List<ChatMessageResponse> messages = Arrays.asList(
                createMockResponse("msg001", "이전 메시지 1"),
                createMockResponse("msg002", "이전 메시지 2")
        );
        
        ChatHistoryResponse mockResponse = ChatHistoryResponse.builder()
                .messages(messages)
                .lastMessageId("msg002")
                .hasMore(false)
                .totalCount(2)
                .build();

        when(chatService.getChatHistory(roomId, beforeMessageId, 50))
                .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .param("before", beforeMessageId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].content").value("이전 메시지 1"))
                .andExpect(jsonPath("$.messages[1].content").value("이전 메시지 2"))
                .andExpect(jsonPath("$.lastMessageId").value("msg002"))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    void 커스텀_limit_파라미터_테스트() throws Exception {
        // Given
        int customLimit = 10;
        List<ChatMessageResponse> messages = Arrays.asList(
                createMockResponse("msg001", "메시지 1"),
                createMockResponse("msg002", "메시지 2")
        );
        
        ChatHistoryResponse mockResponse = ChatHistoryResponse.builder()
                .messages(messages)
                .lastMessageId("msg002")
                .hasMore(true)
                .totalCount(2)
                .build();

        when(chatService.getChatHistory(roomId, null, customLimit))
                .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .param("limit", String.valueOf(customLimit))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.lastMessageId").value("msg002"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    void 빈_히스토리_응답() throws Exception {
        // Given
        ChatHistoryResponse emptyResponse = ChatHistoryResponse.builder()
                .messages(Collections.emptyList())
                .lastMessageId(null)
                .hasMore(false)
                .totalCount(0)
                .build();

        when(chatService.getChatHistory(roomId, null, 50))
                .thenReturn(emptyResponse);

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(0))
                .andExpect(jsonPath("$.lastMessageId").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void hasMore_true_상황_테스트() throws Exception {
        // Given
        List<ChatMessageResponse> messages = Arrays.asList(
                createMockResponse("msg001", "메시지 1"),
                createMockResponse("msg002", "메시지 2"),
                createMockResponse("msg003", "메시지 3")
        );
        
        ChatHistoryResponse response = ChatHistoryResponse.builder()
                .messages(messages)
                .lastMessageId("msg003")
                .hasMore(true) // 더 많은 메시지가 있음
                .totalCount(3)
                .build();

        when(chatService.getChatHistory(eq(roomId), isNull(), eq(3)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .param("limit", "3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.lastMessageId").value("msg003"));
    }

    @Test
    void hasMore_false_상황_테스트() throws Exception {
        // Given
        List<ChatMessageResponse> messages = Arrays.asList(
                createMockResponse("msg001", "마지막 메시지")
        );
        
        ChatHistoryResponse response = ChatHistoryResponse.builder()
                .messages(messages)
                .lastMessageId("msg001")
                .hasMore(false) // 더 이상 메시지가 없음
                .totalCount(1)
                .build();

        when(chatService.getChatHistory(roomId, null, 50))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.lastMessageId").value("msg001"));
    }

    @Test
    void User_ID_헤더_없을_때_실패() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()); // MissingRequestHeaderException으로 400 에러 발생
    }

    @Test
    void 여러_파라미터_조합_테스트() throws Exception {
        // Given
        String beforeMessageId = "msg100";
        int customLimit = 25;
        
        List<ChatMessageResponse> messages = Arrays.asList(
                createMockResponse("msg050", "중간 메시지")
        );
        
        ChatHistoryResponse response = ChatHistoryResponse.builder()
                .messages(messages)
                .lastMessageId("msg050")
                .hasMore(true)
                .totalCount(1)
                .build();

        when(chatService.getChatHistory(roomId, beforeMessageId, customLimit))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .param("before", beforeMessageId)
                        .param("limit", String.valueOf(customLimit))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].messageId").value("msg050"))
                .andExpect(jsonPath("$.lastMessageId").value("msg050"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void 다양한_메시지_타입_응답() throws Exception {
        // Given
        List<ChatMessageResponse> diverseMessages = Arrays.asList(
                createMockResponseWithType("msg001", "일반 채팅", "CHAT"),
                createMockResponseWithType("msg002", "사용자 입장", "JOIN"),
                createMockResponseWithType("msg003", "결제 요청", "PAYMENT_REQUEST")
        );
        
        ChatHistoryResponse response = ChatHistoryResponse.builder()
                .messages(diverseMessages)
                .lastMessageId("msg003")
                .hasMore(false)
                .totalCount(3)
                .build();

        when(chatService.getChatHistory(roomId, null, 50))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", roomId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(3))
                .andExpect(jsonPath("$.messages[0].messageType").value("CHAT"))
                .andExpect(jsonPath("$.messages[1].messageType").value("JOIN"))
                .andExpect(jsonPath("$.messages[2].messageType").value("PAYMENT_REQUEST"));
    }

    @Test
    void 레거시_API_엔드포인트_테스트() throws Exception {
        // Given
        List<ChatMessageResponse> messages = Arrays.asList(
                createMockResponse("msg001", "레거시 메시지 1"),
                createMockResponse("msg002", "레거시 메시지 2")
        );

        when(chatService.getRecentMessages(roomId, 50))
                .thenReturn(messages);

        // When & Then
        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages/legacy", roomId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].messageId").value("msg001"))
                .andExpect(jsonPath("$[1].messageId").value("msg002"));
    }

    private ChatMessageResponse createMockResponse(String messageId, String content) {
        return createMockResponseWithType(messageId, content, "CHAT");
    }

    private ChatMessageResponse createMockResponseWithType(String messageId, String content, String messageType) {
        return ChatMessageResponse.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(userId)
                .studentId("20230001")
                .senderName("테스트사용자")
                .profileImageUrl("https://example.com/profile.jpg")
                .content(content)
                .messageType(messageType)
                .timestamp(LocalDateTime.now())
                .build();
    }
}