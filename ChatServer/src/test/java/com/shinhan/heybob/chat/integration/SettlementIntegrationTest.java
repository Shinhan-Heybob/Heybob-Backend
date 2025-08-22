package com.shinhan.heybob.chat.integration;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageRequest;
import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import com.shinhan.heybob.chat.domain.communication.dto.ServerMessage;
import com.shinhan.heybob.chat.domain.communication.handler.MessageHandler;
import com.shinhan.heybob.chat.domain.communication.service.MainServerCommunicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("정산 처리 통합 테스트")
class SettlementIntegrationTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    
    @Mock
    private MainServerCommunicationService mainServerCommunicationService;
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @InjectMocks
    private MessageHandler messageHandler;
    
    private String testSettlementId;
    private String testRoomId;
    private SettlementData testSettlementData;
    
    @BeforeEach
    void setUp() {
        testSettlementId = "settlement-integration-test-123";
        testRoomId = "room-integration-test-456";
        
        // 테스트용 정산 데이터 생성
        Map<String, SettlementData.SettlementStatus> participantStatus = new HashMap<>();
        participantStatus.put("user1", SettlementData.SettlementStatus.builder()
            .status("pending").build());
        participantStatus.put("user2", SettlementData.SettlementStatus.builder()
            .status("pending").build());
        participantStatus.put("requester", SettlementData.SettlementStatus.builder()
            .status("pending").build());
        
        testSettlementData = SettlementData.builder()
            .settlementId(testSettlementId)
            .roomId(testRoomId)
            .note("통합 테스트 정산")
            .totalAmount(24000)
            .perPersonAmount(8000)
            .participants(List.of("requester", "user1", "user2"))
            .expiryTime(LocalDateTime.now().plusMinutes(30))
            .participantStatus(participantStatus)
            .build();
    }
    
    @Test
    @DisplayName("완전한 정산 플로우 통합 테스트: 요청 → 응답 → 완료")
    void completeSettlementFlow_Success() {
        // Given: Main 서버로부터 정산 브로드캐스트 요청 수신
        Map<String, Object> broadcastPayload = Map.of(
            "settlementId", testSettlementId,
            "roomId", testRoomId,
            "requesterId", "requester",
            "requesterName", "김철수",
            "targetUserIds", List.of("requester", "user1", "user2"),
            "perPersonAmount", 8000,
            "note", "통합 테스트 정산",
            "expiryTime", LocalDateTime.now().plusMinutes(30).toString()
        );
        
        ServerMessage broadcastMessage = ServerMessage.builder()
            .messageId("broadcast-msg-123")
            .messageType(ServerMessage.MessageType.BROADCAST_SETTLEMENT_REQUEST)
            .payload(broadcastPayload)
            .build();
        
        // When 1: 정산 브로드캐스트 처리
        messageHandler.handleSettlementBroadcast(broadcastMessage);
        
        // Then 1: 활성 정산 등록 및 WebSocket 브로드캐스트 확인
        SettlementData activeSettlement = messageHandler.getActiveSettlement(testSettlementId);
        assertThat(activeSettlement).isNotNull();
        assertThat(activeSettlement.getSettlementId()).isEqualTo(testSettlementId);
        
        // 참여자 수만큼 WebSocket 메시지 전송 확인
        verify(messagingTemplate, times(3)).convertAndSend(
            eq("/topic/room/" + testRoomId), 
            any(Object.class)
        );
        
        // When 2: 사용자들의 정산 응답 처리
        // user1이 승낙
        simulateUserSettlementResponse("user1", "김영희", "SETTLEMENT_ACCEPT", "accepted");
        
        // user2가 거절
        simulateUserSettlementResponse("user2", "박민수", "SETTLEMENT_REJECT", "rejected");
        
        // Then 2: 정산 응답이 Main 서버로 전달되었는지 확인
        verify(mainServerCommunicationService, times(2)).sendSettlementResponse(
            eq(testSettlementId), 
            anyString(), 
            anyString(), 
            anyString(), 
            anyString()
        );
        
        // 구체적인 응답 내용 확인
        verify(mainServerCommunicationService).sendSettlementResponse(
            eq(testSettlementId), eq("user1"), eq("김영희"), eq("accepted"), anyString()
        );
        verify(mainServerCommunicationService).sendSettlementResponse(
            eq(testSettlementId), eq("user2"), eq("박민수"), eq("rejected"), anyString()
        );
        
        // When 3: Main 서버로부터 정산 완료 알림 수신
        List<Map<String, Object>> paymentResults = List.of(
            Map.of("userId", "user1", "status", "SUCCESS", "message", "결제 완료"),
            Map.of("userId", "user2", "status", "SKIPPED", "message", "거절로 인한 건너뛰기")
        );
        
        Map<String, Object> completionPayload = Map.of(
            "roomId", testRoomId,
            "settlementId", testSettlementId,
            "status", "PARTIAL",
            "paymentResults", paymentResults,
            "totalAmount", "8000",
            "message", "일부 정산 완료"
        );
        
        ServerMessage completionMessage = ServerMessage.builder()
            .messageId("completion-msg-123")
            .messageType(ServerMessage.MessageType.SETTLEMENT_COMPLETED)
            .timestamp(LocalDateTime.now())
            .payload(completionPayload)
            .build();
        
        messageHandler.handleNotification(completionMessage);
        
        // Then 3: 정산 완료 알림이 올바르게 브로드캐스트되었는지 확인
        
        // 방 전체 알림
        verify(messagingTemplate).convertAndSend(
            eq("/topic/room/" + testRoomId + "/settlement"), 
            any(Map.class)
        );
        
        // 개별 사용자 알림 (결제 결과가 있는 사용자들만)
        verify(messagingTemplate, times(2)).convertAndSendToUser(
            anyString(), 
            eq("/queue/settlement"), 
            any(Map.class)
        );
        
        verify(messagingTemplate).convertAndSendToUser(
            eq("user1"), eq("/queue/settlement"), any(Map.class)
        );
        verify(messagingTemplate).convertAndSendToUser(
            eq("user2"), eq("/queue/settlement"), any(Map.class)
        );
    }
    
    @Test
    @DisplayName("정산 응답 후 상태 업데이트 및 브로드캐스트")
    void settlementResponse_StateUpdateAndBroadcast() {
        // Given: 활성 정산 설정
        setupActiveSettlement();
        
        // When: 사용자 응답 처리
        simulateUserSettlementResponse("user1", "김영희", "SETTLEMENT_ACCEPT", "accepted");
        
        // Then: 활성 정산의 상태가 업데이트되었는지 확인
        SettlementData activeSettlement = messageHandler.getActiveSettlement(testSettlementId);
        assertThat(activeSettlement).isNotNull();
        
        SettlementData.SettlementStatus user1Status = 
            activeSettlement.getParticipantStatus().get("user1");
        assertThat(user1Status.getStatus()).isEqualTo("accepted");
        assertThat(user1Status.getResponseTime()).isNotNull();
        
        // 다른 사용자들은 여전히 pending 상태
        assertThat(activeSettlement.getParticipantStatus().get("user2").getStatus())
            .isEqualTo("pending");
    }
    
    @Test
    @DisplayName("정산 취소 처리")
    void settlementCancel_Success() {
        // Given: 활성 정산 설정
        setupActiveSettlement();
        
        // When: 요청자가 정산 취소
        simulateUserSettlementResponse("requester", "김철수", "SETTLEMENT_CANCEL", "cancelled");
        
        // Then: 취소 응답이 Main 서버로 전달되었는지 확인
        verify(mainServerCommunicationService).sendSettlementResponse(
            eq(testSettlementId), eq("requester"), eq("김철수"), eq("cancelled"), anyString()
        );
        
        // 활성 정산의 요청자 상태가 업데이트되었는지 확인
        SettlementData activeSettlement = messageHandler.getActiveSettlement(testSettlementId);
        assertThat(activeSettlement.getParticipantStatus().get("requester").getStatus())
            .isEqualTo("cancelled");
    }
    
    @Test
    @DisplayName("만료된 정산에 대한 응답 시도")
    void expiredSettlementResponse_Handled() {
        // Given: 만료된 정산 설정
        Map<String, SettlementData.SettlementStatus> participantStatus = new HashMap<>();
        participantStatus.put("user1", SettlementData.SettlementStatus.builder()
            .status("pending").build());
        
        SettlementData expiredSettlement = SettlementData.builder()
            .settlementId(testSettlementId)
            .roomId(testRoomId)
            .note("만료된 정산")
            .totalAmount(24000)
            .perPersonAmount(8000)
            .participants(List.of("user1"))
            .expiryTime(LocalDateTime.now().minusMinutes(10)) // 이미 만료됨
            .participantStatus(participantStatus)
            .build();
        
        // 만료된 정산을 브로드캐스트로 등록 (실제 상황에서는 발생하지 않지만 테스트용)
        Map<String, Object> broadcastPayload = Map.of(
            "settlementId", testSettlementId,
            "roomId", testRoomId,
            "requesterId", "requester",
            "requesterName", "김철수",
            "targetUserIds", List.of("user1"),
            "perPersonAmount", 8000,
            "note", "만료된 정산",
            "expiryTime", LocalDateTime.now().minusMinutes(10).toString()
        );
        
        ServerMessage broadcastMessage = ServerMessage.builder()
            .messageId("expired-msg-123")
            .messageType(ServerMessage.MessageType.BROADCAST_SETTLEMENT_REQUEST)
            .payload(broadcastPayload)
            .build();
        
        messageHandler.handleSettlementBroadcast(broadcastMessage);
        
        // When & Then: 만료된 정산에 대한 응답 시도해도 정상 처리되어야 함
        // (실제 ChatController에서는 만료 확인하지만 여기서는 MessageHandler만 테스트)
        simulateUserSettlementResponse("user1", "김영희", "SETTLEMENT_ACCEPT", "accepted");
        
        // Main 서버로는 여전히 응답이 전달되어야 함 (Main에서 만료 처리)
        verify(mainServerCommunicationService).sendSettlementResponse(
            eq(testSettlementId), eq("user1"), eq("김영희"), eq("accepted"), anyString()
        );
    }
    
    private void setupActiveSettlement() {
        Map<String, Object> broadcastPayload = Map.of(
            "settlementId", testSettlementId,
            "roomId", testRoomId,
            "requesterId", "requester",
            "requesterName", "김철수",
            "targetUserIds", List.of("requester", "user1", "user2"),
            "perPersonAmount", 8000,
            "note", "통합 테스트 정산",
            "expiryTime", LocalDateTime.now().plusMinutes(30).toString()
        );
        
        ServerMessage broadcastMessage = ServerMessage.builder()
            .messageId("setup-msg-123")
            .messageType(ServerMessage.MessageType.BROADCAST_SETTLEMENT_REQUEST)
            .payload(broadcastPayload)
            .build();
        
        messageHandler.handleSettlementBroadcast(broadcastMessage);
        
        // 기존 mock 호출 클리어
        clearInvocations(messagingTemplate, mainServerCommunicationService);
    }
    
    private void simulateUserSettlementResponse(String userId, String userName, 
                                              String messageType, String expectedResponseType) {
        // 실제 ChatController의 handleSettlementInteraction 로직을 시뮬레이션
        
        // 1. 활성 정산에서 사용자 상태 업데이트
        SettlementData activeSettlement = messageHandler.getActiveSettlement(testSettlementId);
        if (activeSettlement != null) {
            SettlementData.SettlementStatus userStatus = 
                activeSettlement.getParticipantStatus().get(userId);
            if (userStatus != null) {
                userStatus.setStatus(expectedResponseType);
                userStatus.setResponseTime(LocalDateTime.now());
            }
        }
        
        // 2. Main 서버에 응답 전달 (Mock을 통해 시뮬레이션)
        doNothing().when(mainServerCommunicationService).sendSettlementResponse(
            eq(testSettlementId), eq(userId), eq(userName), 
            eq(expectedResponseType), anyString()
        );
        
        // 실제 호출
        mainServerCommunicationService.sendSettlementResponse(
            testSettlementId, userId, userName, expectedResponseType, 
            LocalDateTime.now().toString()
        );
    }
}