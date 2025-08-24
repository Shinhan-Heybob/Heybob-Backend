package com.shinhan.heybob.chat.domain.chat.scheduler;

import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import com.shinhan.heybob.chat.domain.chat.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementSchedulerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SettlementService settlementService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private SettlementScheduler settlementScheduler;

    @BeforeEach
    void setUp() {
        // Mock 설정은 각 테스트에서 필요시 수행
    }

    @Test
    void 만료된_정산_확인_및_처리_테스트() {
        // Given
        String expiredSettlementKey = "settlement:expired-001";
        String validSettlementKey = "settlement:valid-001";
        
        Set<String> settlementKeys = Set.of(expiredSettlementKey, validSettlementKey);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.keys("settlement:*")).thenReturn(settlementKeys);
        
        // 만료된 정산 데이터
        SettlementData expiredSettlement = createMockSettlementData("expired-001", "test-room-001");
        expiredSettlement.setExpiryTime(LocalDateTime.now().minusMinutes(5)); // 5분 전 만료
        when(valueOperations.get(expiredSettlementKey)).thenReturn(expiredSettlement);
        
        // 유효한 정산 데이터
        SettlementData validSettlement = createMockSettlementData("valid-001", "test-room-002");
        validSettlement.setExpiryTime(LocalDateTime.now().plusMinutes(20)); // 20분 후 만료
        when(valueOperations.get(validSettlementKey)).thenReturn(validSettlement);

        // When
        settlementScheduler.checkExpiredSettlements();

        // Then
        // 만료된 정산만 처리되어야 함
        verify(settlementService, times(1)).handleSettlementTimeout("expired-001");
        verify(settlementService, never()).handleSettlementTimeout("valid-001");
        
        // 만료된 정산은 Redis에서 삭제
        verify(redisTemplate, times(1)).delete(expiredSettlementKey);
        verify(redisTemplate, never()).delete(validSettlementKey);
        
        // 만료 브로드캐스트
        verify(messagingTemplate, times(1)).convertAndSend(
            "/topic/room/test-room-001/settlement/timeout", 
            expiredSettlement
        );
        verify(messagingTemplate, never()).convertAndSend(
            eq("/topic/room/test-room-002/settlement/timeout"), 
            any(SettlementData.class)
        );
    }

    @Test
    void 정산_키가_없을_때_테스트() {
        // Given
        when(redisTemplate.keys("settlement:*")).thenReturn(null);

        // When
        settlementScheduler.checkExpiredSettlements();

        // Then
        verify(settlementService, never()).handleSettlementTimeout(any());
        verify(redisTemplate, never()).delete(any(String.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(SettlementData.class));
    }

    @Test
    void Redis_조회_실패_시_예외_처리_테스트() {
        // Given
        when(redisTemplate.keys("settlement:*")).thenThrow(new RuntimeException("Redis 연결 실패"));

        // When & Then - 예외가 발생해도 스케줄러는 계속 동작해야 함
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            settlementScheduler.checkExpiredSettlements();
        });

        // 후속 처리는 실행되지 않아야 함
        verify(settlementService, never()).handleSettlementTimeout(any());
        verify(redisTemplate, never()).delete(any(String.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(SettlementData.class));
    }

    @Test
    void null_정산_데이터_처리_테스트() {
        // Given
        String settlementKey = "settlement:null-data";
        Set<String> settlementKeys = Set.of(settlementKey);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.keys("settlement:*")).thenReturn(settlementKeys);
        when(valueOperations.get(settlementKey)).thenReturn(null);

        // When
        settlementScheduler.checkExpiredSettlements();

        // Then - null 데이터는 무시되어야 함
        verify(settlementService, never()).handleSettlementTimeout(any());
        verify(redisTemplate, never()).delete(any(String.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(SettlementData.class));
    }

    @Test
    void roomId가_null인_정산_처리_테스트() {
        // Given
        String settlementKey = "settlement:no-room-id";
        Set<String> settlementKeys = Set.of(settlementKey);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.keys("settlement:*")).thenReturn(settlementKeys);
        
        SettlementData settlementWithoutRoom = createMockSettlementData("no-room-id", null);
        settlementWithoutRoom.setExpiryTime(LocalDateTime.now().minusMinutes(1)); // 만료됨
        when(valueOperations.get(settlementKey)).thenReturn(settlementWithoutRoom);

        // When
        settlementScheduler.checkExpiredSettlements();

        // Then
        verify(settlementService, times(1)).handleSettlementTimeout("no-room-id");
        verify(redisTemplate, times(1)).delete(settlementKey);
        // roomId가 null이므로 브로드캐스트는 안됨
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(SettlementData.class));
    }

    private SettlementData createMockSettlementData(String settlementId, String roomId) {
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