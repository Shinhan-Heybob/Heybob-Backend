package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.SettlementData;
import com.shinhan.heybob.chat.domain.communication.service.MainServerCommunicationService;
import com.shinhan.heybob.chat.global.error.ChatException;
import com.shinhan.heybob.chat.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementServiceImpl implements SettlementService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final MainServerCommunicationService mainServerCommunicationService;
    private static final String SETTLEMENT_KEY_PREFIX = "settlement:";
    
    @Override
    public SettlementData createSettlement(String roomId, String requesterId, String note, Integer totalAmount) {
        try {
            // Mock 데이터로 방 멤버 조회
            List<String> roomMembers = getRoomMembers(roomId);
            
            String settlementId = UUID.randomUUID().toString();
            Integer perPersonAmount = totalAmount / roomMembers.size();
            LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(30);
            
            // 초기 참가자 상태 설정
            Map<String, SettlementData.SettlementStatus> participantStatus = new HashMap<>();
            for (String memberId : roomMembers) {
                participantStatus.put(memberId, SettlementData.SettlementStatus.builder()
                        .status("pending")
                        .build());
            }
            
            SettlementData settlementData = SettlementData.builder()
                    .settlementId(settlementId)
                    .roomId(roomId)
                    .note(note)
                    .totalAmount(totalAmount)
                    .perPersonAmount(perPersonAmount)
                    .participants(roomMembers)
                    .expiryTime(expiryTime)
                    .participantStatus(participantStatus)
                    .build();
            
            // Redis에 저장 (TTL 30분)
            String redisKey = SETTLEMENT_KEY_PREFIX + settlementId;
            redisTemplate.opsForValue().set(redisKey, settlementData, 30, TimeUnit.MINUTES);
            
            log.info("정산 생성 완료: settlementId={}, roomId={}, requesterId={}", settlementId, roomId, requesterId);
            return settlementData;
            
        } catch (Exception e) {
            log.error("정산 생성 실패: roomId={}, requesterId={}", roomId, requesterId, e);
            throw new ChatException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
    
    @Override
    public SettlementData updateSettlementResponse(String settlementId, String userId, String responseType) {
        try {
            SettlementData settlement = getSettlement(settlementId);
            if (settlement == null) {
                throw new ChatException(ErrorCode.MESSAGE_NOT_FOUND);
            }
            
            // 만료 확인
            if (LocalDateTime.now().isAfter(settlement.getExpiryTime())) {
                throw new ChatException(ErrorCode.INVALID_REQUEST);
            }
            
            // 사용자 응답 업데이트
            SettlementData.SettlementStatus userStatus = settlement.getParticipantStatus().get(userId);
            if (userStatus != null) {
                userStatus.setStatus(responseType);  // "accepted" or "rejected"
                userStatus.setResponseTime(LocalDateTime.now());
            }
            
            // Redis 업데이트
            String redisKey = SETTLEMENT_KEY_PREFIX + settlementId;
            redisTemplate.opsForValue().set(redisKey, settlement, 30, TimeUnit.MINUTES);
            
            // 모든 참가자가 응답했는지 확인
            boolean allResponded = settlement.getParticipantStatus().values().stream()
                    .allMatch(status -> !"pending".equals(status.getStatus()));
            
            if (allResponded) {
                processSettlementCompletion(settlementId);
            }
            
            log.info("정산 응답 업데이트: settlementId={}, userId={}, responseType={}", settlementId, userId, responseType);
            return settlement;
            
        } catch (ChatException e) {
            throw e;
        } catch (Exception e) {
            log.error("정산 응답 업데이트 실패: settlementId={}, userId={}", settlementId, userId, e);
            throw new ChatException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
    
    @Override
    public SettlementData getSettlement(String settlementId) {
        try {
            String redisKey = SETTLEMENT_KEY_PREFIX + settlementId;
            return (SettlementData) redisTemplate.opsForValue().get(redisKey);
        } catch (Exception e) {
            log.error("정산 조회 실패: settlementId={}", settlementId, e);
            return null;
        }
    }
    
    @Override
    public List<String> getRoomMembers(String roomId) {
        try {
            // Main 서버에서 실제 방 멤버 정보 조회
            List<Map<String, Object>> members = mainServerCommunicationService
                .getRoomMembers(roomId, "system")
                .get(); // 동기 처리 (30초 타임아웃)
            
            return members.stream()
                .map(member -> (String) member.get("userId"))
                .toList();
                
        } catch (Exception e) {
            log.error("Main 서버에서 방 멤버 조회 실패, Mock 데이터 사용: roomId={}", roomId, e);
            // Fallback: Mock 데이터 사용
            return Arrays.asList("20000622", "20000623", "20000624");
        }
    }
    
    @Override
    public void processSettlementCompletion(String settlementId) {
        try {
            SettlementData settlement = getSettlement(settlementId);
            if (settlement == null) return;
            
            // 승낙한 사용자들만 추출
            List<String> acceptedUsers = settlement.getParticipantStatus().entrySet().stream()
                    .filter(entry -> "accepted".equals(entry.getValue().getStatus()))
                    .map(Map.Entry::getKey)
                    .toList();
            
            if (!acceptedUsers.isEmpty()) {
                // Main 서버에 실제 결제 처리 요청 (비동기)
                mainServerCommunicationService.processSettlement(
                    settlementId,
                    settlement.getRoomId(),
                    acceptedUsers,
                    settlement.getPerPersonAmount(),
                    settlement.getNote(),
                    "system"
                ).thenAccept(response -> {
                    log.info("✅ 정산 처리 요청 전송 완료: settlementId={}, acceptedUsers={}", 
                        settlementId, acceptedUsers);
                }).exceptionally(throwable -> {
                    log.error("❌ 정산 처리 요청 실패: settlementId={}", settlementId, throwable);
                    return null;
                });
            } else {
                log.info("정산 승낙한 사용자가 없음: settlementId={}", settlementId);
            }
            
        } catch (Exception e) {
            log.error("정산 완료 처리 실패: settlementId={}", settlementId, e);
        }
    }
    
    @Override
    public void handleSettlementTimeout(String settlementId) {
        try {
            log.info("정산 시간 만료 처리: settlementId={}", settlementId);
            // TODO: 시간 만료 알림 브로드캐스트
        } catch (Exception e) {
            log.error("정산 시간 만료 처리 실패: settlementId={}", settlementId, e);
        }
    }
}