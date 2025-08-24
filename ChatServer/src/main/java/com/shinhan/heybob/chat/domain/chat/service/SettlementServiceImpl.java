package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.PaymentRequestData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementServiceImpl implements SettlementService {
    
    @Override
    public PaymentRequestData createSettlement(String roomId, String requesterId, String note, Integer totalAmount) {
        log.info("결제 요청 생성: roomId={}, requesterId={}, note={}, amount={}", roomId, requesterId, note, totalAmount);
        
        // 단순화된 결제 요청 데이터 생성 (실제 처리는 Main 서버에서)
        return PaymentRequestData.builder()
                .settlementId(UUID.randomUUID().toString())
                .roomId(roomId)
                .requesterName("사용자")
                .requestAmount(totalAmount != null ? totalAmount : 12000)
                .settlementUrl("/main/settlement/" + UUID.randomUUID().toString())
                .build();
    }
    
    @Override
    public PaymentRequestData updateSettlementResponse(String settlementId, String userId, String responseType) {
        log.info("결제 응답 (단순화): settlementId={}, userId={}, responseType={}", settlementId, userId, responseType);
        // 실제 처리는 Main 서버에서 담당
        return null;
    }
    
    @Override
    public PaymentRequestData getSettlement(String settlementId) {
        log.debug("결제 조회 (단순화): settlementId={}", settlementId);
        return null;
    }
    
    @Override
    public List<String> getRoomMembers(String roomId) {
        log.debug("방 멤버 조회 (Mock): roomId={}", roomId);
        return Arrays.asList("20000622", "20000623");
    }
    
    @Override
    public void processSettlementCompletion(String settlementId) {
        log.info("결제 완료 처리 (단순화): settlementId={}", settlementId);
    }
    
    @Override
    public void handleSettlementTimeout(String settlementId) {
        log.info("결제 시간 만료 처리 (단순화): settlementId={}", settlementId);
    }
}