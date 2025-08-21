package com.shinhan.heybob.chat.domain.chat.scheduler;

import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import com.shinhan.heybob.chat.domain.chat.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementScheduler {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final SettlementService settlementService;
    private final SimpMessagingTemplate messagingTemplate;
    
    private static final String SETTLEMENT_KEY_PATTERN = "settlement:*";
    
    @Scheduled(fixedRate = 60000)  // 1분마다 실행
    public void checkExpiredSettlements() {
        try {
            Set<String> settlementKeys = redisTemplate.keys(SETTLEMENT_KEY_PATTERN);
            if (settlementKeys == null) return;
            
            for (String key : settlementKeys) {
                SettlementData settlement = (SettlementData) redisTemplate.opsForValue().get(key);
                if (settlement == null) continue;
                
                // 만료 시간 확인
                if (LocalDateTime.now().isAfter(settlement.getExpiryTime())) {
                    handleExpiredSettlement(settlement);
                    // Redis에서 제거
                    redisTemplate.delete(key);
                }
            }
            
        } catch (Exception e) {
            log.error("정산 만료 확인 스케줄러 오류", e);
        }
    }
    
    private void handleExpiredSettlement(SettlementData settlement) {
        try {
            log.info("정산 만료 처리: settlementId={}", settlement.getSettlementId());
            
            // 정산 만료 처리
            settlementService.handleSettlementTimeout(settlement.getSettlementId());
            
            // 정산 만료 브로드캐스트
            String roomId = settlement.getRoomId();
            if (roomId != null) {
                messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId + "/settlement/timeout", 
                    settlement
                );
            }
            
        } catch (Exception e) {
            log.error("정산 만료 처리 실패: settlementId={}", settlement.getSettlementId(), e);
        }
    }
}