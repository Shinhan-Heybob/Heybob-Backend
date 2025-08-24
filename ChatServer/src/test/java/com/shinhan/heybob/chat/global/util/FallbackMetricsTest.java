package com.shinhan.heybob.chat.global.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fallback 메트릭 테스트")
class FallbackMetricsTest {

    private FallbackMetrics fallbackMetrics;
    
    @BeforeEach
    void setUp() {
        fallbackMetrics = new FallbackMetrics();
    }
    
    @Test
    @DisplayName("Redis Stream 성공 카운트 증가")
    void incrementRedisStreamSuccess() {
        // Given
        long initialCount = fallbackMetrics.getRedisStreamSuccessCount();
        
        // When
        for (int i = 0; i < 5; i++) {
            fallbackMetrics.incrementRedisStreamSuccess();
        }
        
        // Then
        assertThat(fallbackMetrics.getRedisStreamSuccessCount())
            .isEqualTo(initialCount + 5);
    }
    
    @Test
    @DisplayName("MongoDB Fallback 카운트 증가")
    void incrementMongodbFallback() {
        // Given
        long initialCount = fallbackMetrics.getMongodbFallbackCount();
        
        // When
        for (int i = 0; i < 3; i++) {
            fallbackMetrics.incrementMongodbFallback();
        }
        
        // Then
        assertThat(fallbackMetrics.getMongodbFallbackCount())
            .isEqualTo(initialCount + 3);
    }
    
    @Test
    @DisplayName("전체 실패 카운트 증가")
    void incrementTotalFailure() {
        // Given
        long initialCount = fallbackMetrics.getTotalFailureCount();
        
        // When
        fallbackMetrics.incrementTotalFailure();
        fallbackMetrics.incrementTotalFailure();
        
        // Then
        assertThat(fallbackMetrics.getTotalFailureCount())
            .isEqualTo(initialCount + 2);
    }
    
    @Test
    @DisplayName("Fallback 비율 계산 - 정상 케이스")
    void getFallbackRate_NormalCase() {
        // Given
        // Redis 성공 80건, Fallback 20건
        for (int i = 0; i < 80; i++) {
            fallbackMetrics.incrementRedisStreamSuccess();
        }
        for (int i = 0; i < 20; i++) {
            fallbackMetrics.incrementMongodbFallback();
        }
        
        // When
        double fallbackRate = fallbackMetrics.getFallbackRate();
        
        // Then
        assertThat(fallbackRate).isEqualTo(20.0); // 20% Fallback 비율
    }
    
    @Test
    @DisplayName("Fallback 비율 계산 - 전체가 0인 경우")
    void getFallbackRate_ZeroTotal() {
        // Given
        // 아무것도 처리하지 않은 상태
        
        // When
        double fallbackRate = fallbackMetrics.getFallbackRate();
        
        // Then
        assertThat(fallbackRate).isEqualTo(0.0);
    }
    
    @Test
    @DisplayName("Fallback 비율 계산 - 모두 실패인 경우")
    void getFallbackRate_AllFallback() {
        // Given
        // Redis 성공 0건, Fallback 10건
        for (int i = 0; i < 10; i++) {
            fallbackMetrics.incrementMongodbFallback();
        }
        
        // When
        double fallbackRate = fallbackMetrics.getFallbackRate();
        
        // Then
        assertThat(fallbackRate).isEqualTo(100.0); // 100% Fallback 비율
    }
    
    @Test
    @DisplayName("Fallback 비율 계산 - 모두 성공인 경우")
    void getFallbackRate_AllSuccess() {
        // Given
        // Redis 성공 50건, Fallback 0건
        for (int i = 0; i < 50; i++) {
            fallbackMetrics.incrementRedisStreamSuccess();
        }
        
        // When
        double fallbackRate = fallbackMetrics.getFallbackRate();
        
        // Then
        assertThat(fallbackRate).isEqualTo(0.0); // 0% Fallback 비율
    }
    
    @Test
    @DisplayName("통계 로깅 - 예외 없이 완료")
    void logCurrentStats_NoException() {
        // Given
        fallbackMetrics.incrementRedisStreamSuccess();
        fallbackMetrics.incrementMongodbFallback();
        fallbackMetrics.incrementTotalFailure();
        
        // When & Then
        // 예외 없이 완료되어야 함
        fallbackMetrics.logCurrentStats();
    }
    
    @Test
    @DisplayName("동시성 테스트 - 여러 스레드에서 카운터 증가")
    void concurrentIncrement() throws InterruptedException {
        // Given
        int threadCount = 10;
        int incrementPerThread = 100;
        
        Thread[] threads = new Thread[threadCount];
        
        // When
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementPerThread; j++) {
                    fallbackMetrics.incrementRedisStreamSuccess();
                    fallbackMetrics.incrementMongodbFallback();
                    if (j % 10 == 0) {
                        fallbackMetrics.incrementTotalFailure();
                    }
                }
            });
            threads[i].start();
        }
        
        // 모든 스레드 완료 대기
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Then
        assertThat(fallbackMetrics.getRedisStreamSuccessCount())
            .isEqualTo(threadCount * incrementPerThread);
        assertThat(fallbackMetrics.getMongodbFallbackCount())
            .isEqualTo(threadCount * incrementPerThread);
        assertThat(fallbackMetrics.getTotalFailureCount())
            .isEqualTo(threadCount * (incrementPerThread / 10));
    }
    
    @Test
    @DisplayName("메트릭 초기 상태 확인")
    void initialState() {
        // Given & When
        // 새로 생성된 메트릭 객체
        
        // Then
        assertThat(fallbackMetrics.getRedisStreamSuccessCount()).isZero();
        assertThat(fallbackMetrics.getMongodbFallbackCount()).isZero();
        assertThat(fallbackMetrics.getTotalFailureCount()).isZero();
        assertThat(fallbackMetrics.getFallbackRate()).isEqualTo(0.0);
    }
    
    @Test
    @DisplayName("대량 데이터 처리 테스트")
    void largeVolumeTest() {
        // Given
        int largeVolume = 1_000_000;
        
        // When
        for (int i = 0; i < largeVolume; i++) {
            if (i % 100 == 0) {
                fallbackMetrics.incrementMongodbFallback();
            } else {
                fallbackMetrics.incrementRedisStreamSuccess();
            }
        }
        
        // Then
        assertThat(fallbackMetrics.getRedisStreamSuccessCount())
            .isEqualTo(largeVolume - (largeVolume / 100));
        assertThat(fallbackMetrics.getMongodbFallbackCount())
            .isEqualTo(largeVolume / 100);
        
        double expectedFallbackRate = 1.0; // 1% Fallback
        assertThat(fallbackMetrics.getFallbackRate())
            .isCloseTo(expectedFallbackRate, org.assertj.core.data.Percentage.withPercentage(0.01));
    }
    
    @Test
    @DisplayName("메트릭 수집 시나리오 테스트")
    void metricCollectionScenario() {
        // Given - 실제 운영 시나리오 시뮬레이션
        // 1시간 동안의 메시지 처리 시뮬레이션
        
        // 정상 처리 (95%)
        for (int i = 0; i < 950; i++) {
            fallbackMetrics.incrementRedisStreamSuccess();
        }
        
        // Fallback 처리 (4%)
        for (int i = 0; i < 40; i++) {
            fallbackMetrics.incrementMongodbFallback();
        }
        
        // 완전 실패 (1%)
        for (int i = 0; i < 10; i++) {
            fallbackMetrics.incrementTotalFailure();
        }
        
        // When
        double fallbackRate = fallbackMetrics.getFallbackRate();
        
        // Then
        assertThat(fallbackMetrics.getRedisStreamSuccessCount()).isEqualTo(950);
        assertThat(fallbackMetrics.getMongodbFallbackCount()).isEqualTo(40);
        assertThat(fallbackMetrics.getTotalFailureCount()).isEqualTo(10);
        
        // Fallback 비율 = 40 / (950 + 40) * 100 ≈ 4.04%
        assertThat(fallbackRate).isCloseTo(4.04, org.assertj.core.data.Percentage.withPercentage(0.1));
        
        // 통계 로깅도 정상 동작해야 함
        fallbackMetrics.logCurrentStats();
    }
}