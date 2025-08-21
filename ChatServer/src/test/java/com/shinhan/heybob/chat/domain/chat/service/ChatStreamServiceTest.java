package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;
import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import com.shinhan.heybob.chat.domain.chat.model.ChatMessage;
import com.shinhan.heybob.chat.domain.chat.repository.ChatRepository;
import com.shinhan.heybob.chat.global.error.ChatException;
import com.shinhan.heybob.chat.global.error.ErrorCode;
import com.shinhan.heybob.chat.global.util.FallbackMetrics;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Chat Stream Service Fallback 테스트")
class ChatStreamServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private ChatRepository chatRepository;
    
    @Mock
    private FallbackMetrics fallbackMetrics;
    
    @Mock
    private StreamOperations<String, Object, Object> streamOperations;
    
    @InjectMocks
    private ChatStreamServiceImpl chatStreamService;
    
    private ChatMessageResponse testMessage;
    
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        
        // 테스트용 메시지 생성
        testMessage = ChatMessageResponse.builder()
            .messageId("test-msg-123")
            .roomId("room-456")
            .senderId("user-789")
            .studentId("20000622")
            .senderName("김철수")
            .profileImageUrl("https://example.com/profile.jpg")
            .content("정산 요청 메시지")
            .messageType("PAYMENT_REQUEST")
            .timestamp(LocalDateTime.now())
            .settlementData(SettlementData.builder()
                .settlementId("settlement-123")
                .note("치킨값 정산")
                .totalAmount(24000)
                .build())
            .build();
    }
    
    @Test
    @DisplayName("Redis Stream 저장 성공")
    void saveToStream_RedisSuccess() {
        // Given
        when(streamOperations.add(anyString(), any(Map.class))).thenReturn(null);
        
        // When
        chatStreamService.saveToStream(testMessage);
        
        // Then
        verify(streamOperations).add(eq("room:messages:" + testMessage.getRoomId()), any(Map.class));
        verify(fallbackMetrics).incrementRedisStreamSuccess();
        verify(chatRepository, never()).save(any(ChatMessage.class));
        
        // Stream 데이터 구조 검증
        ArgumentCaptor<Map<String, Object>> messageDataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(streamOperations).add(anyString(), messageDataCaptor.capture());
        
        Map<String, Object> messageData = messageDataCaptor.getValue();
        assertThat(messageData.get("messageId")).isEqualTo(testMessage.getMessageId());
        assertThat(messageData.get("senderId")).isEqualTo(testMessage.getSenderId());
        assertThat(messageData.get("content")).isEqualTo(testMessage.getContent());
        assertThat(messageData.get("messageType")).isEqualTo(testMessage.getMessageType());
        assertThat(messageData.get("timestamp")).isEqualTo(testMessage.getTimestamp().toString());
    }
    
    @Test
    @DisplayName("Redis Stream 실패 → MongoDB Fallback 성공")
    void saveToStream_RedisFail_MongoFallbackSuccess() {
        // Given
        RuntimeException redisException = new RuntimeException("Redis connection failed");
        when(streamOperations.add(anyString(), any(Map.class))).thenThrow(redisException);
        when(chatRepository.save(any(ChatMessage.class))).thenReturn(any(ChatMessage.class));
        
        // When
        chatStreamService.saveToStream(testMessage);
        
        // Then
        // Redis Stream 시도했지만 실패
        verify(streamOperations).add(anyString(), any(Map.class));
        
        // MongoDB Fallback 실행됨
        ArgumentCaptor<ChatMessage> mongoMessageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatRepository).save(mongoMessageCaptor.capture());
        
        ChatMessage savedMessage = mongoMessageCaptor.getValue();
        assertThat(savedMessage.getId()).isEqualTo(testMessage.getMessageId());
        assertThat(savedMessage.getRoomId()).isEqualTo(testMessage.getRoomId());
        assertThat(savedMessage.getSenderId()).isEqualTo(testMessage.getSenderId());
        assertThat(savedMessage.getContent()).isEqualTo(testMessage.getContent());
        assertThat(savedMessage.getMessageType()).isEqualTo(ChatMessage.MessageType.PAYMENT_REQUEST);
        assertThat(savedMessage.getEmergencyFallback()).isTrue(); // Fallback 플래그 확인
        assertThat(savedMessage.getSettlementData()).isNotNull();
        
        // 메트릭 업데이트 확인
        verify(fallbackMetrics, never()).incrementRedisStreamSuccess();
        verify(fallbackMetrics).incrementMongodbFallback();
        verify(fallbackMetrics, never()).incrementTotalFailure();
    }
    
    @Test
    @DisplayName("Redis Stream + MongoDB 모두 실패")
    void saveToStream_BothFail_ExceptionThrown() {
        // Given
        RuntimeException redisException = new RuntimeException("Redis connection failed");
        RuntimeException mongoException = new RuntimeException("MongoDB connection failed");
        
        when(streamOperations.add(anyString(), any(Map.class))).thenThrow(redisException);
        when(chatRepository.save(any(ChatMessage.class))).thenThrow(mongoException);
        
        // When & Then
        assertThatThrownBy(() -> chatStreamService.saveToStream(testMessage))
            .isInstanceOf(ChatException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MESSAGE_SAVE_FAILED);
        
        // Redis 시도 확인
        verify(streamOperations).add(anyString(), any(Map.class));
        
        // MongoDB Fallback 시도 확인
        verify(chatRepository).save(any(ChatMessage.class));
        
        // 메트릭 업데이트 확인
        verify(fallbackMetrics, never()).incrementRedisStreamSuccess();
        verify(fallbackMetrics, never()).incrementMongodbFallback();
        verify(fallbackMetrics).incrementTotalFailure();
    }
    
    @Test
    @DisplayName("Settlement Data가 없는 일반 메시지 Fallback")
    void saveToStream_RegularMessage_FallbackSuccess() {
        // Given
        ChatMessageResponse regularMessage = ChatMessageResponse.builder()
            .messageId("regular-msg-123")
            .roomId("room-456")
            .senderId("user-789")
            .studentId("20000622")
            .senderName("김철수")
            .content("안녕하세요")
            .messageType("CHAT")
            .timestamp(LocalDateTime.now())
            .settlementData(null) // 일반 메시지는 정산 데이터 없음
            .build();
        
        RuntimeException redisException = new RuntimeException("Redis connection failed");
        when(streamOperations.add(anyString(), any(Map.class))).thenThrow(redisException);
        when(chatRepository.save(any(ChatMessage.class))).thenReturn(any(ChatMessage.class));
        
        // When
        chatStreamService.saveToStream(regularMessage);
        
        // Then
        ArgumentCaptor<ChatMessage> mongoMessageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatRepository).save(mongoMessageCaptor.capture());
        
        ChatMessage savedMessage = mongoMessageCaptor.getValue();
        assertThat(savedMessage.getSettlementData()).isNull();
        assertThat(savedMessage.getMessageType()).isEqualTo(ChatMessage.MessageType.CHAT);
        assertThat(savedMessage.getEmergencyFallback()).isTrue();
    }
    
    @Test
    @DisplayName("메시지 데이터 변환 테스트")
    void createMessageData_CorrectStructure() {
        // Given
        when(streamOperations.add(anyString(), any(Map.class))).thenReturn(null);
        
        // When
        chatStreamService.saveToStream(testMessage);
        
        // Then
        ArgumentCaptor<Map<String, Object>> messageDataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(streamOperations).add(anyString(), messageDataCaptor.capture());
        
        Map<String, Object> messageData = messageDataCaptor.getValue();
        
        // 필수 필드 존재 확인
        assertThat(messageData).containsKeys(
            "messageId", "senderId", "studentId", "senderName", 
            "profileImageUrl", "content", "messageType", "timestamp"
        );
        
        // 데이터 타입 확인
        assertThat(messageData.get("messageId")).isInstanceOf(String.class);
        assertThat(messageData.get("senderId")).isInstanceOf(String.class);
        assertThat(messageData.get("content")).isInstanceOf(String.class);
        assertThat(messageData.get("messageType")).isInstanceOf(String.class);
        assertThat(messageData.get("timestamp")).isInstanceOf(String.class);
        
        // 실제 값 확인
        assertThat(messageData.get("messageId")).isEqualTo("test-msg-123");
        assertThat(messageData.get("roomId")).isNull(); // roomId는 stream key에 포함되므로 data에는 없어야 함
        assertThat(messageData.get("messageType")).isEqualTo("PAYMENT_REQUEST");
    }
    
    @Test
    @DisplayName("동시성 테스트 - 여러 메시지 동시 처리")
    void saveToStream_ConcurrentMessages() throws InterruptedException {
        // Given
        int messageCount = 10;
        ChatMessageResponse[] messages = new ChatMessageResponse[messageCount];
        
        for (int i = 0; i < messageCount; i++) {
            messages[i] = ChatMessageResponse.builder()
                .messageId("concurrent-msg-" + i)
                .roomId("concurrent-room")
                .senderId("user-" + i)
                .content("동시 메시지 " + i)
                .messageType("PAYMENT_REQUEST")
                .timestamp(LocalDateTime.now())
                .build();
        }
        
        // Redis는 성공하도록 설정
        when(streamOperations.add(anyString(), any(Map.class))).thenReturn(null);
        
        // When - 여러 스레드에서 동시 처리
        Thread[] threads = new Thread[messageCount];
        for (int i = 0; i < messageCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> chatStreamService.saveToStream(messages[index]));
            threads[i].start();
        }
        
        // 모든 스레드 완료 대기
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Then
        verify(streamOperations, times(messageCount)).add(anyString(), any(Map.class));
        verify(fallbackMetrics, times(messageCount)).incrementRedisStreamSuccess();
        verify(chatRepository, never()).save(any(ChatMessage.class));
    }
    
    @Test
    @DisplayName("부분 실패 시나리오 - 일부는 Redis 성공, 일부는 Fallback")
    void saveToStream_PartialFailureScenario() {
        // Given
        ChatMessageResponse successMessage = testMessage;
        ChatMessageResponse failMessage = ChatMessageResponse.builder()
            .messageId("fail-msg-123")
            .roomId("room-456")
            .senderId("user-789")
            .content("실패할 메시지")
            .messageType("PAYMENT_REQUEST")
            .timestamp(LocalDateTime.now())
            .build();
        
        // 첫 번째 메시지는 성공, 두 번째는 실패하도록 설정
        when(streamOperations.add(anyString(), any(Map.class)))
            .thenReturn(null) // 첫 번째 호출은 성공
            .thenThrow(new RuntimeException("Redis connection failed for this message")); // 두 번째 호출은 실패
        
        when(chatRepository.save(any(ChatMessage.class))).thenReturn(any(ChatMessage.class));
        
        // When
        chatStreamService.saveToStream(successMessage); // 성공해야 함
        chatStreamService.saveToStream(failMessage);    // Fallback되어야 함
        
        // Then
        verify(streamOperations, times(2)).add(anyString(), any(Map.class));
        verify(chatRepository, times(1)).save(any(ChatMessage.class)); // Fallback 1회만
        
        verify(fallbackMetrics, times(1)).incrementRedisStreamSuccess();
        verify(fallbackMetrics, times(1)).incrementMongodbFallback();
        verify(fallbackMetrics, never()).incrementTotalFailure();
    }
}