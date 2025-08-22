package com.shinhan.heybob.chat.domain.communication.service;

import com.shinhan.heybob.chat.domain.communication.dto.ServerMessage;
import com.shinhan.heybob.chat.domain.communication.util.RetryMechanism;
import com.shinhan.heybob.chat.global.error.ChatException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Main 서버 통신 서비스 테스트")
class MainServerCommunicationServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private RetryMechanism retryMechanism;
    
    @Mock
    private StreamOperations<String, Object, Object> streamOperations;
    
    @InjectMocks
    private MainServerCommunicationServiceImpl communicationService;
    
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
    }
    
    @Test
    @DisplayName("메시지 전송 성공 테스트")
    void sendMessage_Success() {
        // Given
        ServerMessage message = ServerMessage.builder()
            .messageId("test-message-id")
            .messageType(ServerMessage.MessageType.GET_ROOM_MEMBERS)
            .sourceServer("CHAT")
            .targetServer("MAIN")
            .timestamp(LocalDateTime.now())
            .build();
        
        when(streamOperations.add(anyString(), any(Map.class))).thenReturn(null);
        
        // When
        communicationService.sendMessage(message);
        
        // Then
        ArgumentCaptor<String> streamKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(streamOperations).add(streamKeyCaptor.capture(), dataCaptor.capture());
        
        assertThat(streamKeyCaptor.getValue()).isEqualTo("chat-to-main-stream");
        assertThat(dataCaptor.getValue()).containsKey("messageId");
        assertThat(dataCaptor.getValue().get("messageId")).isEqualTo("test-message-id");
        assertThat(dataCaptor.getValue().get("messageType")).isEqualTo("GET_ROOM_MEMBERS");
    }
    
    @Test
    @DisplayName("메시지 전송 실패시 재시도 스케줄링")
    void sendMessage_FailureWithRetry() {
        // Given
        ServerMessage message = ServerMessage.builder()
            .messageId("test-message-id")
            .messageType(ServerMessage.MessageType.PROCESS_SETTLEMENT)
            .sourceServer("CHAT")
            .targetServer("MAIN")
            .timestamp(LocalDateTime.now())
            .build();
        
        RuntimeException redisException = new RuntimeException("Redis connection failed");
        when(streamOperations.add(anyString(), any(Map.class))).thenThrow(redisException);
        
        // When & Then
        assertThatThrownBy(() -> communicationService.sendMessage(message))
            .isInstanceOf(ChatException.class);
            
        verify(retryMechanism).scheduleRetry(eq(message), eq(redisException), any(Runnable.class));
    }
    
    @Test
    @DisplayName("방 멤버 조회 성공")
    void getRoomMembers_Success() throws ExecutionException, InterruptedException {
        // Given
        String roomId = "test-room-123";
        String requesterId = "user-456";
        
        when(streamOperations.add(anyString(), any(Map.class))).thenReturn(null);
        
        // 응답 시뮬레이션
        ServerMessage responseMessage = ServerMessage.builder()
            .messageId("response-id")
            .messageType(ServerMessage.MessageType.ROOM_MEMBERS_RESPONSE)
            .payload(Map.of(
                "members", List.of(
                    Map.of("userId", "user1", "userName", "김철수"),
                    Map.of("userId", "user2", "userName", "이영희")
                )
            ))
            .build();
        
        // When
        CompletableFuture<List<Map<String, Object>>> future = communicationService.getRoomMembers(roomId, requesterId);
        
        // 응답 처리 시뮬레이션
        communicationService.handleResponse(responseMessage);
        
        // Then
        verify(streamOperations).add(eq("chat-to-main-stream"), any(Map.class));
        
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(streamOperations).add(anyString(), dataCaptor.capture());
        
        Map<String, Object> sentData = dataCaptor.getValue();
        assertThat(sentData.get("messageType")).isEqualTo("GET_ROOM_MEMBERS");
        assertThat(sentData.get("payload_roomId")).isEqualTo(roomId);
        assertThat(sentData.get("payload_requesterId")).isEqualTo(requesterId);
    }
    
    @Test
    @DisplayName("정산 처리 요청 전송")
    void processSettlement_Success() {
        // Given
        String settlementId = "settlement-123";
        String roomId = "room-456";
        List<String> acceptedUsers = List.of("user1", "user2");
        Integer perPersonAmount = 8000;
        String note = "치킨값 정산";
        String requesterId = "requester-789";
        
        when(streamOperations.add(anyString(), any(Map.class))).thenReturn(null);
        
        // When
        CompletableFuture<ServerMessage> future = communicationService.processSettlement(
            settlementId, roomId, acceptedUsers, perPersonAmount, note, requesterId
        );
        
        // Then
        verify(streamOperations).add(eq("chat-to-main-stream"), any(Map.class));
        
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(streamOperations).add(anyString(), dataCaptor.capture());
        
        Map<String, Object> sentData = dataCaptor.getValue();
        assertThat(sentData.get("messageType")).isEqualTo("PROCESS_SETTLEMENT");
        assertThat(sentData.get("payload_settlementId")).isEqualTo(settlementId);
        assertThat(sentData.get("payload_roomId")).isEqualTo(roomId);
        assertThat(sentData.get("payload_perPersonAmount")).isEqualTo(perPersonAmount);
        assertThat(sentData.get("payload_note")).isEqualTo(note);
    }
    
    @Test
    @DisplayName("정산 응답 전송")
    void sendSettlementResponse_Success() {
        // Given
        String settlementId = "settlement-123";
        String userId = "user-456";
        String userName = "김철수";
        String response = "accepted";
        String responseTime = "2024-01-15T10:30:00";
        
        when(streamOperations.add(anyString(), any(Map.class))).thenReturn(null);
        
        // When
        communicationService.sendSettlementResponse(settlementId, userId, userName, response, responseTime);
        
        // Then
        verify(streamOperations).add(eq("chat-to-main-stream"), any(Map.class));
        
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(streamOperations).add(anyString(), dataCaptor.capture());
        
        Map<String, Object> sentData = dataCaptor.getValue();
        assertThat(sentData.get("messageType")).isEqualTo("SETTLEMENT_RESPONSE");
        assertThat(sentData.get("payload_settlementId")).isEqualTo(settlementId);
        assertThat(sentData.get("payload_userId")).isEqualTo(userId);
        assertThat(sentData.get("payload_userName")).isEqualTo(userName);
        assertThat(sentData.get("payload_response")).isEqualTo(response);
    }
    
    @Test
    @DisplayName("Heartbeat 전송")
    void sendHeartbeat_Success() {
        // Given
        when(streamOperations.add(anyString(), any(Map.class))).thenReturn(null);
        
        // When
        communicationService.sendHeartbeat();
        
        // Then
        verify(streamOperations).add(eq("chat-to-main-stream"), any(Map.class));
        
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(streamOperations).add(anyString(), dataCaptor.capture());
        
        Map<String, Object> sentData = dataCaptor.getValue();
        assertThat(sentData.get("messageType")).isEqualTo("HEARTBEAT");
        assertThat(sentData.get("sourceServer")).isEqualTo("CHAT");
        assertThat(sentData.get("targetServer")).isEqualTo("MAIN");
    }
    
    @Test
    @DisplayName("응답 처리 - 대기 중인 Future 완료")
    void handleResponse_CompletePendingFuture() {
        // Given
        String correlationId = "correlation-123";
        ServerMessage requestMessage = ServerMessage.builder()
            .messageId(correlationId)
            .messageType(ServerMessage.MessageType.GET_ROOM_MEMBERS)
            .timestamp(LocalDateTime.now())
            .build();
        
        ServerMessage responseMessage = ServerMessage.builder()
            .correlationId(correlationId)
            .messageType(ServerMessage.MessageType.ROOM_MEMBERS_RESPONSE)
            .payload(Map.of("members", List.of()))
            .build();
        
        when(streamOperations.add(anyString(), any(Map.class))).thenReturn(null);
        
        // When
        CompletableFuture<ServerMessage> future = communicationService.sendMessageWithResponse(requestMessage, 30000);
        communicationService.handleResponse(responseMessage);
        
        // Then
        assertThat(future).isCompleted();
        assertThat(future.join().getCorrelationId()).isEqualTo(correlationId);
    }
}