package com.shinhan.heybob.chat.domain.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import com.shinhan.heybob.chat.domain.chat.service.ChatService;
import com.shinhan.heybob.chat.domain.chat.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private SettlementService settlementService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatController chatController;
    private String roomId;
    private String userId;

    @BeforeEach
    void setUp() {
        roomId = "test-room-001";
        userId = "20000622";
    }

    @Test
    void 정산_승낙_버튼_상호작용_테스트() {
        // Given
        String settlementId = "test-settlement-001";
        ChatMessageRequest request = new ChatMessageRequest();
        request.setRoomId(roomId);
        request.setMessageType("SETTLEMENT_ACCEPT");
        request.setContent("정산 승낙합니다");
        request.setSettlementId(settlementId);

        SettlementData updatedSettlement = createMockSettlementData(settlementId);
        updatedSettlement.getParticipantStatus().get(userId).setStatus("accepted");

        when(settlementService.updateSettlementResponse(settlementId, userId, "accepted"))
                .thenReturn(updatedSettlement);

        ChatMessageResponse mockResponse = ChatMessageResponse.builder()
                .messageId("response-001")
                .roomId(roomId)
                .senderId(userId)
                .messageType("SETTLEMENT_ACCEPT")
                .content("정산 승낙합니다")
                .timestamp(LocalDateTime.now())
                .build();

        when(chatService.processMessage(eq(roomId), eq(userId), any(), any(), any(), eq(request)))
                .thenReturn(mockResponse);

        // When
        org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor = 
            org.springframework.messaging.simp.SimpMessageHeaderAccessor.create();
        headerAccessor.setNativeHeader("X-User-Id", userId);
        headerAccessor.setNativeHeader("X-User-Name", "테스트사용자");

        // Then - 예외 없이 실행되는지 확인
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            chatController.sendMessage(roomId, request, headerAccessor);
        });

        // Verify
        verify(settlementService, times(1)).updateSettlementResponse(settlementId, userId, "accepted");
        verify(messagingTemplate, times(1)).convertAndSend("/topic/room/" + roomId + "/settlement", updatedSettlement);
        verify(chatService, times(1)).processMessage(eq(roomId), eq(userId), any(), any(), any(), eq(request));
        verify(messagingTemplate, times(1)).convertAndSend("/topic/room/" + roomId, mockResponse);
    }

    @Test
    void 정산_거절_버튼_상호작용_테스트() {
        // Given
        String settlementId = "test-settlement-001";
        ChatMessageRequest request = new ChatMessageRequest();
        request.setRoomId(roomId);
        request.setMessageType("SETTLEMENT_REJECT");
        request.setContent("정산 거절합니다");
        request.setSettlementId(settlementId);

        SettlementData updatedSettlement = createMockSettlementData(settlementId);
        updatedSettlement.getParticipantStatus().get(userId).setStatus("rejected");

        when(settlementService.updateSettlementResponse(settlementId, userId, "rejected"))
                .thenReturn(updatedSettlement);

        ChatMessageResponse mockResponse = ChatMessageResponse.builder()
                .messageId("response-002")
                .roomId(roomId)
                .senderId(userId)
                .messageType("SETTLEMENT_REJECT")
                .content("정산 거절합니다")
                .timestamp(LocalDateTime.now())
                .build();

        when(chatService.processMessage(eq(roomId), eq(userId), any(), any(), any(), eq(request)))
                .thenReturn(mockResponse);

        // When
        org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor = 
            org.springframework.messaging.simp.SimpMessageHeaderAccessor.create();
        headerAccessor.setNativeHeader("X-User-Id", userId);
        headerAccessor.setNativeHeader("X-User-Name", "테스트사용자");

        // Then
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            chatController.sendMessage(roomId, request, headerAccessor);
        });

        // Verify
        verify(settlementService, times(1)).updateSettlementResponse(settlementId, userId, "rejected");
        verify(messagingTemplate, times(1)).convertAndSend("/topic/room/" + roomId + "/settlement", updatedSettlement);
    }

    @Test
    void 정산_취소_버튼_상호작용_테스트() {
        // Given
        String settlementId = "test-settlement-001";
        ChatMessageRequest request = new ChatMessageRequest();
        request.setRoomId(roomId);
        request.setMessageType("SETTLEMENT_CANCEL");
        request.setContent("정산을 취소합니다");
        request.setSettlementId(settlementId);

        SettlementData updatedSettlement = createMockSettlementData(settlementId);

        when(settlementService.updateSettlementResponse(settlementId, userId, "cancelled"))
                .thenReturn(updatedSettlement);

        ChatMessageResponse mockResponse = ChatMessageResponse.builder()
                .messageId("response-003")
                .roomId(roomId)
                .senderId(userId)
                .messageType("SETTLEMENT_CANCEL")
                .content("정산을 취소합니다")
                .timestamp(LocalDateTime.now())
                .build();

        when(chatService.processMessage(eq(roomId), eq(userId), any(), any(), any(), eq(request)))
                .thenReturn(mockResponse);

        // When
        org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor = 
            org.springframework.messaging.simp.SimpMessageHeaderAccessor.create();
        headerAccessor.setNativeHeader("X-User-Id", userId);
        headerAccessor.setNativeHeader("X-User-Name", "테스트사용자");

        // Then
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            chatController.sendMessage(roomId, request, headerAccessor);
        });

        // Verify
        verify(settlementService, times(1)).updateSettlementResponse(settlementId, userId, "cancelled");
        verify(messagingTemplate, times(1)).convertAndSend("/topic/room/" + roomId + "/settlement", updatedSettlement);
    }

    @Test
    void settlementId_없는_정산_상호작용_테스트() {
        // Given
        ChatMessageRequest request = new ChatMessageRequest();
        request.setRoomId(roomId);
        request.setMessageType("SETTLEMENT_ACCEPT");
        request.setContent("정산 승낙합니다");
        request.setSettlementId(null); // settlementId 없음

        ChatMessageResponse mockResponse = ChatMessageResponse.builder()
                .messageId("response-004")
                .roomId(roomId)
                .senderId(userId)
                .messageType("SETTLEMENT_ACCEPT")
                .content("정산 승낙합니다")
                .timestamp(LocalDateTime.now())
                .build();

        when(chatService.processMessage(eq(roomId), eq(userId), any(), any(), any(), eq(request)))
                .thenReturn(mockResponse);

        // When
        org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor = 
            org.springframework.messaging.simp.SimpMessageHeaderAccessor.create();
        headerAccessor.setNativeHeader("X-User-Id", userId);
        headerAccessor.setNativeHeader("X-User-Name", "테스트사용자");

        // Then
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            chatController.sendMessage(roomId, request, headerAccessor);
        });

        // Verify - settlementService 호출되지 않음
        verify(settlementService, never()).updateSettlementResponse(any(), any(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(SettlementData.class));
        
        // 일반 메시지로 처리됨
        verify(chatService, times(1)).processMessage(eq(roomId), eq(userId), any(), any(), any(), eq(request));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/room/" + roomId), eq(mockResponse));
    }

    private SettlementData createMockSettlementData(String settlementId) {
        Map<String, SettlementData.SettlementStatus> participantStatus = new HashMap<>();
        participantStatus.put("20000622", SettlementData.SettlementStatus.builder().status("pending").build());
        participantStatus.put("20000623", SettlementData.SettlementStatus.builder().status("pending").build());
        participantStatus.put("20000624", SettlementData.SettlementStatus.builder().status("pending").build());

        return SettlementData.builder()
                .settlementId(settlementId)
                .roomId(roomId)
                .note("테스트 정산")
                .totalAmount(15000)
                .perPersonAmount(5000)
                .participants(Arrays.asList("20000622", "20000623", "20000624"))
                .expiryTime(LocalDateTime.now().plusMinutes(30))
                .participantStatus(participantStatus)
                .build();
    }
}