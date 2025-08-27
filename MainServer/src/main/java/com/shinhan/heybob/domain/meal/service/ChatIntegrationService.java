package com.shinhan.heybob.domain.meal.service;

import com.shinhan.heybob.domain.meal.entity.MealAppointment;

public interface ChatIntegrationService {
    
    Long createChatRoom(MealAppointment mealAppointment);
    
    /**
     * Chat 서버에 정산 브로드캐스트 요청 전송
     * @param settlementId 정산 ID  
     * @param chatRoomId 채팅방 ID
     * @param requesterName 요청자 이름
     * @param requestAmount 요청 금액
     * @return 전송된 메시지 ID
     */
    String sendSettlementBroadcast(Long settlementId, Long chatRoomId, Long requesterId, String requesterName,
                                   String requesterStudentId, String requesterProfileImg, Integer requestAmount);

    String sendSettleRequestBroadcast(Long settlementId, Long chatRoomId, Long requesterId, String requesterName,
                                      String requesterStudentId, String requesterProfileImg, Integer requestAmount);
}