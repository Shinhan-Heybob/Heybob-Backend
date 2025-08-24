package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import com.shinhan.heybob.chat.global.error.ChatException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private SettlementServiceImpl settlementService;

    private String roomId;
    private String requesterId;

    @BeforeEach
    void setUp() {
        roomId = "test-room-001";
        requesterId = "20000622";
    }

    @Test
    void 정산_생성_테스트() {
        // Given
        String note = "치킨값 나뉀내요";
        Integer totalAmount = 24000;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        SettlementData result = settlementService.createSettlement(roomId, requesterId, note, totalAmount);

        // Then
        assertNotNull(result);
        assertNotNull(result.getSettlementId());
        assertEquals(roomId, result.getRoomId());
        assertEquals(note, result.getNote());
        assertEquals(totalAmount, result.getTotalAmount());
        assertEquals(8000, result.getPerPersonAmount()); // 24000 / 3명
        assertEquals(Arrays.asList("20000622", "20000623", "20000624"), result.getParticipants());
        assertNotNull(result.getExpiryTime());
        assertTrue(result.getExpiryTime().isAfter(LocalDateTime.now()));
        
        // 모든 참가자가 pending 상태로 초기화
        assertEquals(3, result.getParticipantStatus().size());
        result.getParticipantStatus().forEach((userId, status) -> {
            assertEquals("pending", status.getStatus());
        });

        // Redis 저장 호출 확인
        verify(valueOperations, times(1)).set(
            eq("settlement:" + result.getSettlementId()), 
            eq(result), 
            eq(30L), 
            eq(TimeUnit.MINUTES)
        );
    }

    @Test
    void 정산_응답_업데이트_테스트() {
        // Given
        String settlementId = "test-settlement-001";
        String userId = "20000623";
        String responseType = "accepted";
        
        SettlementData existingSettlement = createMockSettlementData(settlementId);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("settlement:" + settlementId)).thenReturn(existingSettlement);

        // When
        SettlementData result = settlementService.updateSettlementResponse(settlementId, userId, responseType);

        // Then
        assertNotNull(result);
        assertEquals("accepted", result.getParticipantStatus().get(userId).getStatus());
        assertNotNull(result.getParticipantStatus().get(userId).getResponseTime());

        // Redis 업데이트 호출 확인
        verify(valueOperations, times(1)).set(
            eq("settlement:" + settlementId), 
            eq(result), 
            eq(30L), 
            eq(TimeUnit.MINUTES)
        );
    }

    @Test
    void 존재하지_않는_정산_응답_업데이트_실패() {
        // Given
        String settlementId = "non-existing-settlement";
        String userId = "20000623";
        String responseType = "accepted";
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("settlement:" + settlementId)).thenReturn(null);

        // When & Then
        assertThrows(ChatException.class, () -> {
            settlementService.updateSettlementResponse(settlementId, userId, responseType);
        });
    }

    @Test
    void 만료된_정산_응답_업데이트_실패() {
        // Given
        String settlementId = "expired-settlement";
        String userId = "20000623";
        String responseType = "accepted";
        
        SettlementData expiredSettlement = createMockSettlementData(settlementId);
        expiredSettlement.setExpiryTime(LocalDateTime.now().minusMinutes(1)); // 1분 전 만료
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("settlement:" + settlementId)).thenReturn(expiredSettlement);

        // When & Then
        assertThrows(ChatException.class, () -> {
            settlementService.updateSettlementResponse(settlementId, userId, responseType);
        });
    }

    @Test
    void 방_멤버_조회_테스트() {
        // When
        java.util.List<String> members = settlementService.getRoomMembers(roomId);

        // Then
        assertNotNull(members);
        assertEquals(3, members.size());
        assertTrue(members.contains("20000622"));
        assertTrue(members.contains("20000623"));
        assertTrue(members.contains("20000624"));
    }

    @Test
    void 정산_조회_테스트() {
        // Given
        String settlementId = "test-settlement-001";
        SettlementData mockSettlement = createMockSettlementData(settlementId);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("settlement:" + settlementId)).thenReturn(mockSettlement);

        // When
        SettlementData result = settlementService.getSettlement(settlementId);

        // Then
        assertNotNull(result);
        assertEquals(settlementId, result.getSettlementId());
        assertEquals(roomId, result.getRoomId());
    }

    @Test
    void 존재하지_않는_정산_조회_테스트() {
        // Given
        String settlementId = "non-existing-settlement";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("settlement:" + settlementId)).thenReturn(null);

        // When
        SettlementData result = settlementService.getSettlement(settlementId);

        // Then
        assertNull(result);
    }

    @Test
    void 정산_완료_처리_테스트() {
        // Given
        String settlementId = "test-settlement-001";
        SettlementData settlement = createMockSettlementData(settlementId);
        settlement.getParticipantStatus().get("20000623").setStatus("accepted");
        settlement.getParticipantStatus().get("20000624").setStatus("rejected");
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("settlement:" + settlementId)).thenReturn(settlement);

        // When
        assertDoesNotThrow(() -> {
            settlementService.processSettlementCompletion(settlementId);
        });

        // Then - 예외 없이 실행되면 성공
    }

    @Test
    void 정산_시간_만료_처리_테스트() {
        // Given
        String settlementId = "expired-settlement-001";

        // When
        assertDoesNotThrow(() -> {
            settlementService.handleSettlementTimeout(settlementId);
        });

        // Then - 예외 없이 실행되면 성공
    }

    private SettlementData createMockSettlementData(String settlementId) {
        return SettlementData.builder()
                .settlementId(settlementId)
                .roomId(roomId)
                .note("테스트 정산")
                .totalAmount(15000)
                .perPersonAmount(5000)
                .participants(Arrays.asList("20000622", "20000623", "20000624"))
                .expiryTime(LocalDateTime.now().plusMinutes(30))
                .participantStatus(java.util.Map.of(
                    "20000622", SettlementData.SettlementStatus.builder().status("pending").build(),
                    "20000623", SettlementData.SettlementStatus.builder().status("pending").build(),
                    "20000624", SettlementData.SettlementStatus.builder().status("pending").build()
                ))
                .build();
    }
}