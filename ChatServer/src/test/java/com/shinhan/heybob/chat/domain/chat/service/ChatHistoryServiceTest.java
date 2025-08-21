package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.ChatHistoryResponse;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.repository.ChatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatStreamService chatStreamService;

    @InjectMocks
    private ChatServiceImpl chatService;

    private String roomId;
    private List<ChatMessage> mockMessages;

    @BeforeEach
    void setUp() {
        roomId = "test-room-001";
        
        mockMessages = Arrays.asList(
                createMockMessage("msg001", "첫 번째 메시지", LocalDateTime.now().minusMinutes(30)),
                createMockMessage("msg002", "두 번째 메시지", LocalDateTime.now().minusMinutes(20)),
                createMockMessage("msg003", "세 번째 메시지", LocalDateTime.now().minusMinutes(10)),
                createMockMessage("msg004", "네 번째 메시지", LocalDateTime.now().minusMinutes(5)),
                createMockMessage("msg005", "다섯 번째 메시지", LocalDateTime.now())
        );
    }

    @Test
    void 초기_채팅_히스토리_조회_성공() {
        // Given
        int limit = 3;
        List<ChatMessage> limitedMessages = mockMessages.subList(0, 4); // limit+1 = 4개 (hasMore 확인용)
        
        when(chatRepository.findRecentMessagesByRoomId(roomId, limit + 1))
                .thenReturn(limitedMessages);

        // When
        ChatHistoryResponse response = chatService.getChatHistory(roomId, null, limit);

        // Then
        assertNotNull(response);
        assertEquals(3, response.getTotalCount());
        assertEquals("msg003", response.getLastMessageId());
        assertTrue(response.isHasMore()); // 4개 조회했으므로 더 있음
        
        List<ChatMessageResponse> messages = response.getMessages();
        assertEquals(3, messages.size());
        assertEquals("첫 번째 메시지", messages.get(0).getContent());
        assertEquals("두 번째 메시지", messages.get(1).getContent());
        assertEquals("세 번째 메시지", messages.get(2).getContent());
    }

    @Test
    void 특정_메시지_이전_히스토리_조회_성공() {
        // Given
        String beforeMessageId = "msg003";
        int limit = 2;
        List<ChatMessage> beforeMessages = mockMessages.subList(0, 2); // 처음 2개만
        
        when(chatRepository.findMessagesBeforeId(roomId, beforeMessageId, limit + 1))
                .thenReturn(beforeMessages);

        // When
        ChatHistoryResponse response = chatService.getChatHistory(roomId, beforeMessageId, limit);

        // Then
        assertNotNull(response);
        assertEquals(2, response.getTotalCount());
        assertEquals("msg002", response.getLastMessageId());
        assertFalse(response.isHasMore()); // 2개만 조회했으므로 더 없음
        
        List<ChatMessageResponse> messages = response.getMessages();
        assertEquals(2, messages.size());
        assertEquals("첫 번째 메시지", messages.get(0).getContent());
        assertEquals("두 번째 메시지", messages.get(1).getContent());
    }

    @Test
    void 더_많은_히스토리_있을_때_hasMore_true() {
        // Given
        int limit = 2;
        List<ChatMessage> moreMessages = mockMessages.subList(0, 3); // limit+1 = 3개
        
        when(chatRepository.findRecentMessagesByRoomId(roomId, limit + 1))
                .thenReturn(moreMessages);

        // When
        ChatHistoryResponse response = chatService.getChatHistory(roomId, null, limit);

        // Then
        assertEquals(2, response.getTotalCount()); // limit만큼만 반환
        assertEquals("msg002", response.getLastMessageId());
        assertTrue(response.isHasMore()); // 3개 조회했으므로 더 있음
    }

    @Test
    void 더_이상_히스토리_없을_때_hasMore_false() {
        // Given
        int limit = 5;
        List<ChatMessage> allMessages = mockMessages.subList(0, 3); // limit보다 적게
        
        when(chatRepository.findRecentMessagesByRoomId(roomId, limit + 1))
                .thenReturn(allMessages);

        // When
        ChatHistoryResponse response = chatService.getChatHistory(roomId, null, limit);

        // Then
        assertEquals(3, response.getTotalCount());
        assertEquals("msg003", response.getLastMessageId());
        assertFalse(response.isHasMore()); // limit보다 적게 조회했으므로 더 없음
    }

    @Test
    void 빈_히스토리_조회() {
        // Given
        int limit = 10;
        
        when(chatRepository.findRecentMessagesByRoomId(roomId, limit + 1))
                .thenReturn(Collections.emptyList());

        // When
        ChatHistoryResponse response = chatService.getChatHistory(roomId, null, limit);

        // Then
        assertNotNull(response);
        assertEquals(0, response.getTotalCount());
        assertNull(response.getLastMessageId());
        assertFalse(response.isHasMore());
        assertTrue(response.getMessages().isEmpty());
    }

    @Test
    void 단일_메시지_히스토리_조회() {
        // Given
        int limit = 10;
        List<ChatMessage> singleMessage = Collections.singletonList(mockMessages.get(0));
        
        when(chatRepository.findRecentMessagesByRoomId(roomId, limit + 1))
                .thenReturn(singleMessage);

        // When
        ChatHistoryResponse response = chatService.getChatHistory(roomId, null, limit);

        // Then
        assertEquals(1, response.getTotalCount());
        assertEquals("msg001", response.getLastMessageId());
        assertFalse(response.isHasMore());
        assertEquals("첫 번째 메시지", response.getMessages().get(0).getContent());
    }

    @Test
    void limit_1로_페이징_테스트() {
        // Given
        int limit = 1;
        List<ChatMessage> twoMessages = mockMessages.subList(0, 2); // limit+1 = 2개
        
        when(chatRepository.findRecentMessagesByRoomId(roomId, limit + 1))
                .thenReturn(twoMessages);

        // When
        ChatHistoryResponse response = chatService.getChatHistory(roomId, null, limit);

        // Then
        assertEquals(1, response.getTotalCount());
        assertEquals("msg001", response.getLastMessageId());
        assertTrue(response.isHasMore());
        assertEquals(1, response.getMessages().size());
        assertEquals("첫 번째 메시지", response.getMessages().get(0).getContent());
    }

    @Test
    void 메시지_타입별_히스토리_조회() {
        // Given
        List<ChatMessage> diverseMessages = Arrays.asList(
                createMockMessage("msg001", "일반 메시지", LocalDateTime.now().minusMinutes(30), ChatMessage.MessageType.CHAT),
                createMockMessage("msg002", "입장 메시지", LocalDateTime.now().minusMinutes(20), ChatMessage.MessageType.JOIN),
                createMockMessage("msg003", "결제 요청", LocalDateTime.now().minusMinutes(10), ChatMessage.MessageType.PAYMENT_REQUEST)
        );
        
        when(chatRepository.findRecentMessagesByRoomId(roomId, 4))
                .thenReturn(diverseMessages);

        // When
        ChatHistoryResponse response = chatService.getChatHistory(roomId, null, 3);

        // Then
        assertEquals(3, response.getTotalCount());
        assertFalse(response.isHasMore());
        
        List<ChatMessageResponse> messages = response.getMessages();
        assertEquals("CHAT", messages.get(0).getMessageType());
        assertEquals("JOIN", messages.get(1).getMessageType());
        assertEquals("PAYMENT_REQUEST", messages.get(2).getMessageType());
    }

    private ChatMessage createMockMessage(String id, String content, LocalDateTime timestamp) {
        return createMockMessage(id, content, timestamp, ChatMessage.MessageType.CHAT);
    }

    private ChatMessage createMockMessage(String id, String content, LocalDateTime timestamp, ChatMessage.MessageType messageType) {
        return ChatMessage.builder()
                .id(id)
                .roomId(roomId)
                .senderId("test-user")
                .studentId("20230001")
                .senderName("테스트사용자")
                .profileImageUrl("https://example.com/profile.jpg")
                .content(content)
                .messageType(messageType)
                .timestamp(timestamp)
                .build();
    }
}