package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.repository.ChatRepository;
import com.shinhan.heybob.chat.global.error.ChatException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatStreamService chatStreamService;

    @Mock
    private ChatRepository chatRepository;

    @InjectMocks
    private ChatServiceImpl chatService;

    private ChatMessageRequest testRequest;
    private String roomId;
    private String userId;
    private String studentId;
    private String userName;
    private String profileImageUrl;

    @BeforeEach
    void setUp() {
        roomId = "test-room-001";
        userId = "test-user-001";
        studentId = "20230001";
        userName = "테스트사용자";
        profileImageUrl = "https://example.com/profile.jpg";

        testRequest = new ChatMessageRequest();
        testRequest.setRoomId(roomId);
        testRequest.setContent("테스트 메시지");
        testRequest.setMessageType("CHAT");
    }

    @Test
    void 일반_메시지_처리_테스트() {
        // Given
        testRequest.setMessageType("CHAT");

        // When
        ChatMessageResponse response = chatService.processMessage(
                roomId, userId, studentId, userName, profileImageUrl, testRequest);

        // Then
        assertNotNull(response);
        assertEquals(roomId, response.getRoomId());
        assertEquals(userId, response.getSenderId());
        assertEquals(studentId, response.getStudentId());
        assertEquals(userName, response.getSenderName());
        assertEquals(profileImageUrl, response.getProfileImageUrl());
        assertEquals("테스트 메시지", response.getContent());
        assertEquals("CHAT", response.getMessageType());
        assertNotNull(response.getMessageId());
        assertNotNull(response.getTimestamp());

        // 일반 메시지는 MongoDB에 직접 저장되고 Stream에는 저장되지 않음
        verify(chatRepository, times(1)).save(any(ChatMessage.class));
        verify(chatStreamService, never()).saveToStream(any(ChatMessageResponse.class));
    }

    @Test
    void 금융_메시지_처리_테스트() {
        // Given
        testRequest.setMessageType("PAYMENT_REQUEST");
        testRequest.setContent("결제 요청 메시지");

        // When
        ChatMessageResponse response = chatService.processMessage(
                roomId, userId, studentId, userName, profileImageUrl, testRequest);

        // Then
        assertNotNull(response);
        assertEquals("PAYMENT_REQUEST", response.getMessageType());
        assertEquals("결제 요청 메시지", response.getContent());

        // 금융 메시지는 Redis Stream에 저장되고 MongoDB에는 직접 저장되지 않음
        verify(chatStreamService, times(1)).saveToStream(any(ChatMessageResponse.class));
        verify(chatRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void 결제_확인_메시지_처리_테스트() {
        // Given
        testRequest.setMessageType("PAYMENT_CONFIRM");

        // When
        ChatMessageResponse response = chatService.processMessage(
                roomId, userId, studentId, userName, profileImageUrl, testRequest);

        // Then
        assertEquals("PAYMENT_CONFIRM", response.getMessageType());
        verify(chatStreamService, times(1)).saveToStream(any(ChatMessageResponse.class));
        verify(chatRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void 결제_완료_메시지_처리_테스트() {
        // Given
        testRequest.setMessageType("PAYMENT_COMPLETE");

        // When
        ChatMessageResponse response = chatService.processMessage(
                roomId, userId, studentId, userName, profileImageUrl, testRequest);

        // Then
        assertEquals("PAYMENT_COMPLETE", response.getMessageType());
        verify(chatStreamService, times(1)).saveToStream(any(ChatMessageResponse.class));
        verify(chatRepository, never()).save(any(ChatMessage.class));
    }
    
    @Test
    void 정산_요청_메시지_처리_테스트() {
        // Given
        testRequest.setMessageType("PAYMENT_REQUEST");
        testRequest.setContent("치킨값 정산해요!");
        
        SettlementData requestSettlement = SettlementData.builder()
                .note("치킨 24,000원")
                .totalAmount(24000)
                .build();
        testRequest.setSettlementData(requestSettlement);

        // When
        ChatMessageResponse response = chatService.processMessage(
                roomId, userId, studentId, userName, profileImageUrl, testRequest);

        // Then
        assertEquals("PAYMENT_REQUEST", response.getMessageType());
        assertEquals("치킨값 정산해요!", response.getContent());
        assertNotNull(response.getSettlementData());
        assertEquals(24000, response.getSettlementData().getTotalAmount());
        assertEquals(8000, response.getSettlementData().getPerPersonAmount()); // 24000 / 3명 = 8000
        assertEquals("치킨 24,000원", response.getSettlementData().getNote());
        assertNotNull(response.getSettlementData().getSettlementId());
        assertNotNull(response.getUiState());
        assertTrue(response.getUiState().getIsRequester());
        
        verify(chatStreamService, times(1)).saveToStream(any(ChatMessageResponse.class));
        verify(chatRepository, never()).save(any(ChatMessage.class));
    }
    
    @Test
    void 정산_승낙_메시지_처리_테스트() {
        // Given
        testRequest.setMessageType("SETTLEMENT_ACCEPT");
        testRequest.setContent("정산 승낙합니다");
        testRequest.setSettlementId("test-settlement-001");

        // When
        ChatMessageResponse response = chatService.processMessage(
                roomId, userId, studentId, userName, profileImageUrl, testRequest);

        // Then
        assertEquals("SETTLEMENT_ACCEPT", response.getMessageType());
        assertEquals("정산 승낙합니다", response.getContent());
        
        // 정산 상호작용 메시지도 Redis Stream으로 처리
        verify(chatStreamService, times(1)).saveToStream(any(ChatMessageResponse.class));
        verify(chatRepository, never()).save(any(ChatMessage.class));
    }
    
    @Test
    void 정산_거절_메시지_처리_테스트() {
        // Given
        testRequest.setMessageType("SETTLEMENT_REJECT");
        testRequest.setContent("정산 거절합니다");
        testRequest.setSettlementId("test-settlement-001");

        // When
        ChatMessageResponse response = chatService.processMessage(
                roomId, userId, studentId, userName, profileImageUrl, testRequest);

        // Then
        assertEquals("SETTLEMENT_REJECT", response.getMessageType());
        assertEquals("정산 거절합니다", response.getContent());
        
        verify(chatStreamService, times(1)).saveToStream(any(ChatMessageResponse.class));
        verify(chatRepository, never()).save(any(ChatMessage.class));
    }
    
    @Test
    void 정산_취소_메시지_처리_테스트() {
        // Given
        testRequest.setMessageType("SETTLEMENT_CANCEL");
        testRequest.setContent("정산을 취소합니다");
        testRequest.setSettlementId("test-settlement-001");

        // When
        ChatMessageResponse response = chatService.processMessage(
                roomId, userId, studentId, userName, profileImageUrl, testRequest);

        // Then
        assertEquals("SETTLEMENT_CANCEL", response.getMessageType());
        assertEquals("정산을 취소합니다", response.getContent());
        
        verify(chatStreamService, times(1)).saveToStream(any(ChatMessageResponse.class));
        verify(chatRepository, never()).save(any(ChatMessage.class));
    }
    
    @Test
    void 정산_메시지_UI상태_테스트() {
        // Given - 요청자가 아닌 사용자
        testRequest.setMessageType("PAYMENT_REQUEST");
        testRequest.setContent("정산 요청");
        SettlementData settlementData = SettlementData.builder()
                .note("테스트 정산")
                .totalAmount(15000)
                .build();
        testRequest.setSettlementData(settlementData);

        // When
        ChatMessageResponse response = chatService.processMessage(
                roomId, "20000623", studentId, userName, profileImageUrl, testRequest);

        // Then
        assertNotNull(response.getUiState());
        assertTrue(response.getUiState().getIsRequester()); // "PAYMENT_REQUEST" 메시지 타입이라 요청자로 인식
        assertEquals("pending", response.getUiState().getUserResponseStatus());
        assertTrue(response.getUiState().getAvailableActions().contains("cancel"));
        assertTrue(response.getUiState().getAvailableActions().contains("view_details"));
        assertFalse(response.getUiState().getIsExpired());
    }

    @Test
    void JOIN_메시지_처리_테스트() {
        // Given
        testRequest.setMessageType("JOIN");
        testRequest.setContent("사용자가 방에 입장했습니다.");

        // When
        ChatMessageResponse response = chatService.processMessage(
                roomId, userId, studentId, userName, profileImageUrl, testRequest);

        // Then
        assertEquals("JOIN", response.getMessageType());
        assertEquals("사용자가 방에 입장했습니다.", response.getContent());
        
        // JOIN은 일반 메시지로 처리
        verify(chatRepository, times(1)).save(any(ChatMessage.class));
        verify(chatStreamService, never()).saveToStream(any(ChatMessageResponse.class));
    }

    @Test
    void LEAVE_메시지_처리_테스트() {
        // Given
        testRequest.setMessageType("LEAVE");
        testRequest.setContent("사용자가 방을 나갔습니다.");

        // When
        ChatMessageResponse response = chatService.processMessage(
                roomId, userId, studentId, userName, profileImageUrl, testRequest);

        // Then
        assertEquals("LEAVE", response.getMessageType());
        assertEquals("사용자가 방을 나갔습니다.", response.getContent());
        
        // LEAVE는 일반 메시지로 처리
        verify(chatRepository, times(1)).save(any(ChatMessage.class));
        verify(chatStreamService, never()).saveToStream(any(ChatMessageResponse.class));
    }

    @Test
    void 최근_메시지_조회_테스트() {
        // Given
        int limit = 10;
        List<ChatMessage> mockMessages = Arrays.asList(
                createMockChatMessage("msg1", "첫 번째 메시지", ChatMessage.MessageType.CHAT),
                createMockChatMessage("msg2", "두 번째 메시지", ChatMessage.MessageType.CHAT),
                createMockChatMessage("msg3", "세 번째 메시지", ChatMessage.MessageType.JOIN)
        );

        when(chatRepository.findRecentMessagesByRoomId(roomId, limit))
                .thenReturn(mockMessages);

        // When
        List<ChatMessageResponse> responses = chatService.getRecentMessages(roomId, limit);

        // Then
        assertNotNull(responses);
        assertEquals(3, responses.size());
        
        assertEquals("msg1", responses.get(0).getMessageId());
        assertEquals("첫 번째 메시지", responses.get(0).getContent());
        assertEquals("CHAT", responses.get(0).getMessageType());
        
        assertEquals("msg2", responses.get(1).getMessageId());
        assertEquals("두 번째 메시지", responses.get(1).getContent());
        
        assertEquals("msg3", responses.get(2).getMessageId());
        assertEquals("JOIN", responses.get(2).getMessageType());

        verify(chatRepository, times(1)).findRecentMessagesByRoomId(roomId, limit);
    }

    @Test
    void 특정_메시지_이전_메시지_조회_테스트() {
        // Given
        String beforeMessageId = "before-msg-001";
        int limit = 5;
        List<ChatMessage> mockMessages = Arrays.asList(
                createMockChatMessage("msg1", "이전 메시지 1", ChatMessage.MessageType.CHAT),
                createMockChatMessage("msg2", "이전 메시지 2", ChatMessage.MessageType.CHAT)
        );

        when(chatRepository.findMessagesBeforeId(roomId, beforeMessageId, limit))
                .thenReturn(mockMessages);

        // When
        List<ChatMessageResponse> responses = chatService.getMessagesBefore(roomId, beforeMessageId, limit);

        // Then
        assertNotNull(responses);
        assertEquals(2, responses.size());
        
        assertEquals("msg1", responses.get(0).getMessageId());
        assertEquals("이전 메시지 1", responses.get(0).getContent());
        
        assertEquals("msg2", responses.get(1).getMessageId());
        assertEquals("이전 메시지 2", responses.get(1).getContent());

        verify(chatRepository, times(1)).findMessagesBeforeId(roomId, beforeMessageId, limit);
    }

    @Test
    void 빈_메시지_리스트_조회_테스트() {
        // Given
        int limit = 10;
        when(chatRepository.findRecentMessagesByRoomId(roomId, limit))
                .thenReturn(Arrays.asList());

        // When
        List<ChatMessageResponse> responses = chatService.getRecentMessages(roomId, limit);

        // Then
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
        
        verify(chatRepository, times(1)).findRecentMessagesByRoomId(roomId, limit);
    }

    @Test
    void null_content_메시지_처리_예외_테스트() {
        // Given
        testRequest.setContent(null);

        // When & Then
        assertThrows(ChatException.class, () -> {
            chatService.processMessage(roomId, userId, studentId, userName, profileImageUrl, testRequest);
        });
        
        verify(chatRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void 빈_문자열_content_메시지_처리_예외_테스트() {
        // Given
        testRequest.setContent("");

        // When & Then
        assertThrows(ChatException.class, () -> {
            chatService.processMessage(roomId, userId, studentId, userName, profileImageUrl, testRequest);
        });
        
        verify(chatRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void 매우_긴_메시지_처리_테스트() {
        // Given
        String longMessage = "긴 메시지 ".repeat(1000);
        testRequest.setContent(longMessage);

        // When
        ChatMessageResponse response = chatService.processMessage(
                roomId, userId, studentId, userName, profileImageUrl, testRequest);

        // Then
        assertNotNull(response);
        assertEquals(longMessage, response.getContent());
        
        verify(chatRepository, times(1)).save(any(ChatMessage.class));
    }

    private ChatMessage createMockChatMessage(String id, String content, ChatMessage.MessageType messageType) {
        return ChatMessage.builder()
                .id(id)
                .roomId(roomId)
                .senderId(userId)
                .studentId(studentId)
                .senderName(userName)
                .profileImageUrl(profileImageUrl)
                .content(content)
                .messageType(messageType)
                .timestamp(LocalDateTime.now())
                .settlementData(messageType == ChatMessage.MessageType.PAYMENT_REQUEST ? 
                    createMockSettlementData() : null)
                .build();
    }
    
    private SettlementData createMockSettlementData() {
        return SettlementData.builder()
                .settlementId("mock-settlement-001")
                .roomId(roomId)
                .note("테스트 정산")
                .totalAmount(15000)
                .perPersonAmount(5000)
                .participants(Arrays.asList("20000622", "20000623", "20000624"))
                .expiryTime(LocalDateTime.now().plusMinutes(30))
                .build();
    }
}