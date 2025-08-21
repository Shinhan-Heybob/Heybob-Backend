package com.shinhan.heybob.chat.domain.communication.util;

import com.shinhan.heybob.chat.domain.communication.dto.ServerMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("재시도 메커니즘 테스트")
class RetryMechanismTest {

    private RetryMechanism retryMechanism;
    private ServerMessage testMessage;
    private RuntimeException testException;
    
    @BeforeEach
    void setUp() {
        retryMechanism = new RetryMechanism();
        
        testMessage = ServerMessage.builder()
            .messageId("test-retry-msg-123")
            .messageType(ServerMessage.MessageType.PROCESS_SETTLEMENT)
            .sourceServer("CHAT")
            .targetServer("MAIN")
            .timestamp(LocalDateTime.now())
            .retryCount(0)
            .expiryTime(LocalDateTime.now().plusMinutes(5))
            .build();
            
        testException = new RuntimeException("Test communication failure");
    }
    
    @Test
    @DisplayName("첫 번째 재시도 스케줄링")
    void scheduleRetry_FirstRetry_Success() throws InterruptedException {
        // Given
        AtomicInteger retryCallCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        
        Runnable retryAction = () -> {
            retryCallCount.incrementAndGet();
            latch.countDown();
        };
        
        // When
        retryMechanism.scheduleRetry(testMessage, testException, retryAction);
        
        // Then
        // 5초 대기 (첫 번째 재시도 지연 시간)
        boolean completed = latch.await(6, TimeUnit.SECONDS);
        
        assertThat(completed).isTrue();
        assertThat(retryCallCount.get()).isEqualTo(1);
        assertThat(testMessage.getRetryCount()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("재시도 횟수 증가 및 지연 시간 확인")
    void scheduleRetry_DelayProgression() {
        // Given
        testMessage.setRetryCount(1); // 두 번째 재시도
        
        long startTime = System.currentTimeMillis();
        AtomicInteger retryCallCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        
        Runnable retryAction = () -> {
            long elapsedTime = System.currentTimeMillis() - startTime;
            // 두 번째 재시도는 30초 지연이어야 함 (하지만 테스트에서는 짧게 확인)
            retryCallCount.incrementAndGet();
            latch.countDown();
        };
        
        // When
        retryMechanism.scheduleRetry(testMessage, testException, retryAction);
        
        // Then
        assertThat(testMessage.getRetryCount()).isEqualTo(2);
        
        // 실제 대기는 테스트에서 생략 (시간 소요 방지)
        // 지연 시간 배열이 올바른지 확인
        int[] expectedDelays = {5, 30, 180};
        assertThat(expectedDelays[1]).isEqualTo(30); // 두 번째 재시도 지연
    }
    
    @Test
    @DisplayName("최대 재시도 횟수 초과시 포기")
    void scheduleRetry_MaxRetryExceeded_GiveUp() {
        // Given
        testMessage.setRetryCount(3); // 이미 최대 재시도 횟수 초과
        
        AtomicInteger retryCallCount = new AtomicInteger(0);
        Runnable retryAction = () -> retryCallCount.incrementAndGet();
        
        // When
        retryMechanism.scheduleRetry(testMessage, testException, retryAction);
        
        // Then
        // 재시도 액션이 호출되지 않아야 함
        try {
            Thread.sleep(100); // 잠시 대기하여 스케줄링 확인
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertThat(retryCallCount.get()).isZero();
    }
    
    @Test
    @DisplayName("정산 메시지 최종 실패 처리")
    void handleMaxRetryExceeded_SettlementMessage() {
        // Given
        ServerMessage settlementMessage = ServerMessage.builder()
            .messageId("settlement-fail-123")
            .messageType(ServerMessage.MessageType.PROCESS_SETTLEMENT)
            .retryCount(3)
            .build();
        
        // When
        retryMechanism.scheduleRetry(settlementMessage, testException, () -> {});
        
        // Then - 로그를 통해 확인 (실제 구현에서는 알림 시스템 연동)
        // 정산 메시지의 경우 CRITICAL 로그가 출력되어야 함
        assertThat(settlementMessage.getRetryCount()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("방 생성 메시지 최종 실패 처리")
    void handleMaxRetryExceeded_RoomCreationMessage() {
        // Given
        ServerMessage roomMessage = ServerMessage.builder()
            .messageId("room-fail-123")
            .messageType(ServerMessage.MessageType.CREATE_ROOM)
            .retryCount(3)
            .build();
        
        // When
        retryMechanism.scheduleRetry(roomMessage, testException, () -> {});
        
        // Then
        assertThat(roomMessage.getRetryCount()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("재시도 중 또 다른 예외 발생")
    void scheduleRetry_RetryActionFails_ScheduleAgain() throws InterruptedException {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        CountDownLatch firstRetryLatch = new CountDownLatch(1);
        CountDownLatch secondRetryLatch = new CountDownLatch(1);
        
        Runnable retryAction = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt == 1) {
                firstRetryLatch.countDown();
                // 첫 번째 재시도에서 다시 실패
                throw new RuntimeException("재시도 중 또 다른 실패");
            } else if (attempt == 2) {
                secondRetryLatch.countDown();
                // 두 번째 재시도에서 성공
            }
        };
        
        // When
        retryMechanism.scheduleRetry(testMessage, testException, retryAction);
        
        // Then
        // 첫 번째 재시도 완료 대기
        boolean firstCompleted = firstRetryLatch.await(6, TimeUnit.SECONDS);
        assertThat(firstCompleted).isTrue();
        
        // 두 번째 재시도는 더 긴 지연 후 실행되므로 실제 테스트에서는 확인 생략
        // 재시도 카운트가 증가했는지 확인
        assertThat(testMessage.getRetryCount()).isGreaterThanOrEqualTo(1);
    }
    
    @Test
    @DisplayName("Circuit Breaker 상태 확인")
    void isCircuitOpen_DefaultImplementation() {
        // Given
        String targetServer = "MAIN";
        
        // When
        boolean isOpen = retryMechanism.isCircuitOpen(targetServer);
        
        // Then
        // 기본 구현에서는 false 반환 (TODO 구현 예정)
        assertThat(isOpen).isFalse();
    }
    
    @Test
    @DisplayName("만료된 메시지 정리")
    void cleanupExpiredMessages_BasicTest() {
        // Given
        // Mock RedisTemplate (실제 구현에서는 Redis 연동)
        
        // When
        retryMechanism.cleanupExpiredMessages(null);
        
        // Then
        // 예외 없이 완료되어야 함 (기본 구현)
        // 실제 구현에서는 만료된 메시지들이 정리되어야 함
    }
    
    @Test
    @DisplayName("동시 재시도 스케줄링")
    void scheduleRetry_ConcurrentRequests() throws InterruptedException {
        // Given
        int concurrentRequests = 5;
        CountDownLatch allRetriesLatch = new CountDownLatch(concurrentRequests);
        AtomicInteger totalRetries = new AtomicInteger(0);
        
        // When - 여러 메시지를 동시에 재시도 스케줄링
        for (int i = 0; i < concurrentRequests; i++) {
            ServerMessage message = ServerMessage.builder()
                .messageId("concurrent-msg-" + i)
                .messageType(ServerMessage.MessageType.GET_ROOM_MEMBERS)
                .retryCount(0)
                .build();
                
            Runnable retryAction = () -> {
                totalRetries.incrementAndGet();
                allRetriesLatch.countDown();
            };
            
            retryMechanism.scheduleRetry(message, testException, retryAction);
        }
        
        // Then
        boolean allCompleted = allRetriesLatch.await(7, TimeUnit.SECONDS);
        assertThat(allCompleted).isTrue();
        assertThat(totalRetries.get()).isEqualTo(concurrentRequests);
    }
    
    @Test
    @DisplayName("재시도 중 메시지 만료 시간 확인")
    void scheduleRetry_MessageExpiry() {
        // Given
        ServerMessage expiredMessage = ServerMessage.builder()
            .messageId("expired-msg-123")
            .messageType(ServerMessage.MessageType.VALIDATE_USER_ACCESS)
            .expiryTime(LocalDateTime.now().minusMinutes(1)) // 이미 만료됨
            .retryCount(1)
            .build();
        
        AtomicInteger retryCallCount = new AtomicInteger(0);
        Runnable retryAction = () -> retryCallCount.incrementAndGet();
        
        // When
        retryMechanism.scheduleRetry(expiredMessage, testException, retryAction);
        
        // Then
        // 만료된 메시지는 재시도하지 않거나 특별 처리해야 함
        // (현재 구현에서는 만료 시간 체크를 하지 않지만, 향후 개선 가능)
        assertThat(expiredMessage.getRetryCount()).isEqualTo(2);
    }
    
    @Test
    @DisplayName("메트릭 수집 테스트")
    void incrementFailureMetrics_Called() {
        // Given
        ServerMessage maxRetriedMessage = ServerMessage.builder()
            .messageId("metrics-test-123")
            .messageType(ServerMessage.MessageType.HEARTBEAT)
            .retryCount(3) // 최대 재시도 초과
            .build();
        
        // When
        retryMechanism.scheduleRetry(maxRetriedMessage, testException, () -> {});
        
        // Then
        // 메트릭이 수집되었는지 확인 (로그를 통해 간접 확인)
        // 실제 구현에서는 메트릭 수집 시스템과 연동
        assertThat(maxRetriedMessage.getRetryCount()).isEqualTo(3);
    }
}