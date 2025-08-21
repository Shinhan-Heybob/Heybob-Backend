package com.shinhan.heybob.chat.domain.communication.handler;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import com.shinhan.heybob.chat.domain.communication.dto.ServerMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("메시지 핸들러 테스트")
class MessageHandlerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    
    @InjectMocks
    private MessageHandler messageHandler;
    
    @Test
    @DisplayName("정산 브로드캐스트 요청 처리 성공")
    void handleSettlementBroadcast_Success() {
        // Given
        String settlementId = "settlement-123";
        String roomId = "room-456";
        String requesterId = "requester-789";
        String requesterName = "김철수";
        List<String> targetUserIds = List.of("user1", "user2", "user3");
        Integer perPersonAmount = 8000;
        String note = "치킨값 나눠내기";
        String expiryTime = LocalDateTime.now().plusMinutes(30).toString();
        
        Map<String, Object> payload = Map.of(
            "settlementId", settlementId,
            "roomId", roomId,
            "requesterId", requesterId,
            "requesterName", requesterName,
            "targetUserIds", targetUserIds,
            "perPersonAmount", perPersonAmount,
            "note", note,
            "expiryTime", expiryTime
        );
        
        ServerMessage message = ServerMessage.builder()
            .messageId("msg-123")
            .messageType(ServerMessage.MessageType.BROADCAST_SETTLEMENT_REQUEST)
            .payload(payload)
            .build();
        
        // When
        messageHandler.handleSettlementBroadcast(message);
        
        // Then
        // 활성 정산으로 등록되었는지 확인
        SettlementData activeSettlement = messageHandler.getActiveSettlement(settlementId);
        assertThat(activeSettlement).isNotNull();
        assertThat(activeSettlement.getSettlementId()).isEqualTo(settlementId);
        assertThat(activeSettlement.getRoomId()).isEqualTo(roomId);
        assertThat(activeSettlement.getNote()).isEqualTo(note);
        assertThat(activeSettlement.getPerPersonAmount()).isEqualTo(perPersonAmount);
        assertThat(activeSettlement.getParticipants()).hasSize(3);
        
        // 각 참여자의 초기 상태 확인
        assertThat(activeSettlement.getParticipantStatus()).hasSize(3);
        activeSettlement.getParticipantStatus().values().forEach(status -> {
            assertThat(status.getStatus()).isEqualTo("pending");
        });
        
        // WebSocket 브로드캐스트 호출 확인 (참여자 수만큼 호출)
        verify(messagingTemplate, times(targetUserIds.size()))
            .convertAndSend(eq("/topic/room/" + roomId), any(ChatMessageResponse.class));
        
        // 브로드캐스트된 메시지 내용 검증
        ArgumentCaptor<ChatMessageResponse> messageCaptor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), messageCaptor.capture());
        
        ChatMessageResponse broadcastedMessage = messageCaptor.getValue();
        assertThat(broadcastedMessage.getMessageType()).isEqualTo("PAYMENT_REQUEST");
        assertThat(broadcastedMessage.getRoomId()).isEqualTo(roomId);
        assertThat(broadcastedMessage.getSenderId()).isEqualTo(requesterId);
        assertThat(broadcastedMessage.getSenderName()).isEqualTo(requesterName);
        assertThat(broadcastedMessage.getSettlementData()).isNotNull();
        assertThat(broadcastedMessage.getUiState()).isNotNull();
        
        // 메시지 내용 확인
        String expectedContent = String.format("%s님이 정산을 요청했습니다.\n💰 %s\n1인당 %,d원", 
            requesterName, note, perPersonAmount);
        assertThat(broadcastedMessage.getContent()).isEqualTo(expectedContent);
    }
    
    @Test
    @DisplayName("정산 브로드캐스트 - 요청자와 피요청자의 UI 상태 차이")
    void handleSettlementBroadcast_DifferentUiStates() {
        // Given
        String settlementId = "settlement-123";
        String roomId = "room-456";
        String requesterId = "requester-789";
        String requesterName = "김철수";
        List<String> targetUserIds = List.of(requesterId, "user2"); // 요청자도 포함
        Integer perPersonAmount = 8000;
        String note = "치킨값 나눠내기";
        String expiryTime = LocalDateTime.now().plusMinutes(30).toString();
        
        Map<String, Object> payload = Map.of(
            "settlementId", settlementId,
            "roomId", roomId,
            "requesterId", requesterId,
            "requesterName", requesterName,
            "targetUserIds", targetUserIds,
            "perPersonAmount", perPersonAmount,
            "note", note,
            "expiryTime", expiryTime
        );
        
        ServerMessage message = ServerMessage.builder()
            .messageId("msg-123")
            .messageType(ServerMessage.MessageType.BROADCAST_SETTLEMENT_REQUEST)
            .payload(payload)
            .build();
        
        // When
        messageHandler.handleSettlementBroadcast(message);
        
        // Then
        ArgumentCaptor<ChatMessageResponse> messageCaptor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), messageCaptor.capture());
        
        List<ChatMessageResponse> broadcastedMessages = messageCaptor.getAllValues();
        
        // 각 메시지의 UI 상태 확인 (요청자와 피요청자 구분)
        for (ChatMessageResponse msg : broadcastedMessages) {
            assertThat(msg.getUiState()).isNotNull();
            
            boolean isRequester = msg.getSenderId().equals(requesterId);
            assertThat(msg.getUiState().getIsRequester()).isEqualTo(isRequester);
            assertThat(msg.getUiState().getUserResponseStatus()).isEqualTo("pending");
            assertThat(msg.getUiState().getIsExpired()).isFalse();
            
            if (isRequester) {
                // 요청자: [취소하기][자세히보기]
                assertThat(msg.getUiState().getAvailableActions())
                    .containsExactlyInAnyOrder("cancel", "view_details");
            } else {
                // 피요청자: [정산하기][거절하기][자세히보기]
                assertThat(msg.getUiState().getAvailableActions())
                    .containsExactlyInAnyOrder("accept", "reject", "view_details");
            }
        }
    }
    
    @Test
    @DisplayName("정산 완료 알림 처리")
    void handleSettlementCompleted_Success() {
        // Given
        String roomId = "room-456";
        String settlementId = "settlement-123";
        String status = "SUCCESS";
        List<Map<String, Object>> paymentResults = List.of(
            Map.of("userId", "user1", "status", "SUCCESS", "message", "결제 완료"),
            Map.of("userId", "user2", "status", "FAILED", "message", "잔액 부족")
        );
        String totalAmount = "16000";
        String completionMessage = "정산이 완료되었습니다.";
        
        Map<String, Object> payload = Map.of(
            "roomId", roomId,
            "settlementId", settlementId,
            "status", status,
            "paymentResults", paymentResults,
            "totalAmount", totalAmount,
            "message", completionMessage
        );
        
        ServerMessage message = ServerMessage.builder()
            .messageId("msg-123")
            .messageType(ServerMessage.MessageType.SETTLEMENT_COMPLETED)
            .timestamp(LocalDateTime.now())
            .payload(payload)
            .build();
        
        // When
        messageHandler.handleNotification(message);
        
        // Then
        // 방 전체 브로드캐스트 확인
        verify(messagingTemplate).convertAndSend(
            eq("/topic/room/" + roomId + "/settlement"), 
            any(Map.class)
        );
        
        // 개별 사용자 알림 확인 (결제 결과별)
        verify(messagingTemplate, times(2)).convertAndSendToUser(
            anyString(), 
            eq("/queue/settlement"), 
            any(Map.class)
        );
        
        // 방 전체 알림 내용 확인
        ArgumentCaptor<Map<String, Object>> roomNotificationCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(anyString(), roomNotificationCaptor.capture());
        
        Map<String, Object> roomNotification = roomNotificationCaptor.getValue();
        assertThat(roomNotification.get("type")).isEqualTo("SETTLEMENT_COMPLETED");
        assertThat(roomNotification.get("settlementId")).isEqualTo(settlementId);
        assertThat(roomNotification.get("status")).isEqualTo(status);
        assertThat(roomNotification.get("paymentResults")).isEqualTo(paymentResults);
        
        // 개별 사용자 알림 내용 확인
        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> userNotificationCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate, times(2)).convertAndSendToUser(
            userIdCaptor.capture(), anyString(), userNotificationCaptor.capture()
        );
        
        List<String> notifiedUserIds = userIdCaptor.getAllValues();
        assertThat(notifiedUserIds).containsExactlyInAnyOrder("user1", "user2");
        
        List<Map<String, Object>> userNotifications = userNotificationCaptor.getAllValues();
        assertThat(userNotifications).hasSize(2);
        userNotifications.forEach(notification -> {
            assertThat(notification.get("type")).isEqualTo("PERSONAL_SETTLEMENT_RESULT");
            assertThat(notification.get("settlementId")).isEqualTo(settlementId);
        });
    }
    
    @Test
    @DisplayName("멤버 입장 알림 처리")
    void handleMemberJoined_Success() {
        // Given
        String roomId = "room-456";
        String userId = "user-789";
        String userName = "박민수";
        
        Map<String, Object> payload = Map.of(
            "roomId", roomId,
            "userId", userId,
            "userName", userName
        );
        
        ServerMessage message = ServerMessage.builder()
            .messageId("msg-123")
            .messageType(ServerMessage.MessageType.MEMBER_JOINED)
            .timestamp(LocalDateTime.now())
            .payload(payload)
            .build();
        
        // When
        messageHandler.handleNotification(message);
        
        // Then
        verify(messagingTemplate).convertAndSend(
            eq("/topic/room/" + roomId + "/members"), 
            any(Map.class)
        );
        
        ArgumentCaptor<Map<String, Object>> notificationCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(anyString(), notificationCaptor.capture());
        
        Map<String, Object> notification = notificationCaptor.getValue();
        assertThat(notification.get("type")).isEqualTo("MEMBER_JOINED");
        assertThat(notification.get("roomId")).isEqualTo(roomId);
        assertThat(notification.get("userId")).isEqualTo(userId);
        assertThat(notification.get("userName")).isEqualTo(userName);
        assertThat(notification).containsKey("timestamp");
    }
    
    @Test
    @DisplayName("활성 정산 관리")
    void activeSettlementManagement() {
        // Given
        String settlementId = "settlement-123";
        SettlementData settlementData = SettlementData.builder()
            .settlementId(settlementId)
            .roomId("room-456")
            .note("테스트 정산")
            .build();
        
        // When
        // handleSettlementBroadcast를 통해 활성 정산 등록 (실제 테스트에서는 직접 접근 불가)
        // 대신 getter/setter를 통해 테스트
        
        // 정산이 없을 때
        SettlementData notFound = messageHandler.getActiveSettlement("nonexistent");
        assertThat(notFound).isNull();
        
        // 정산 제거
        messageHandler.removeActiveSettlement(settlementId);
        
        // Then
        // 제거 후에는 조회되지 않아야 함
        SettlementData removedSettlement = messageHandler.getActiveSettlement(settlementId);
        assertThat(removedSettlement).isNull();
    }
}