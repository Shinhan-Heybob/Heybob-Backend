package com.shinhan.heybob.chat.global.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class FallbackMetrics {
    
    private final AtomicLong redisStreamSuccessCount = new AtomicLong(0);
    private final AtomicLong mongodbFallbackCount = new AtomicLong(0);
    private final AtomicLong totalFailureCount = new AtomicLong(0);
    
    public void incrementRedisStreamSuccess() {
        long count = redisStreamSuccessCount.incrementAndGet();
        if (count % 100 == 0) {  // 100건마다 로깅
            log.info("📊 Redis Stream 저장 성공: {} 건", count);
        }
    }
    
    public void incrementMongodbFallback() {
        long count = mongodbFallbackCount.incrementAndGet();
        log.warn("⚠️ MongoDB Fallback 발생: {} 건째 (Redis Stream 장애)", count);
        
        if (count % 10 == 0) {  // 10건마다 경고
            log.error("🚨 MongoDB Fallback이 {}건 발생했습니다. Redis 상태를 확인하세요!", count);
        }
    }
    
    public void incrementTotalFailure() {
        long count = totalFailureCount.incrementAndGet();
        log.error("💥 금융 메시지 완전 유실: {} 건째 (Redis + MongoDB 모두 실패)", count);
        
        // 즉시 알림 필요
        log.error("🚨 CRITICAL: 금융 메시지 유실이 {}건 발생했습니다. 즉시 조치가 필요합니다!", count);
    }
    
    public void logCurrentStats() {
        log.info("📈 Fallback 통계 - Redis 성공: {}, MongoDB Fallback: {}, 완전 실패: {}", 
            redisStreamSuccessCount.get(), 
            mongodbFallbackCount.get(), 
            totalFailureCount.get());
    }
    
    // Getter methods for monitoring
    public long getRedisStreamSuccessCount() {
        return redisStreamSuccessCount.get();
    }
    
    public long getMongodbFallbackCount() {
        return mongodbFallbackCount.get();
    }
    
    public long getTotalFailureCount() {
        return totalFailureCount.get();
    }
    
    public double getFallbackRate() {
        long total = redisStreamSuccessCount.get() + mongodbFallbackCount.get();
        return total == 0 ? 0.0 : (double) mongodbFallbackCount.get() / total * 100;
    }
}