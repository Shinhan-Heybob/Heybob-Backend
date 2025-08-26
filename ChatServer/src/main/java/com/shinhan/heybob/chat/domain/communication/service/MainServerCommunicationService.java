package com.shinhan.heybob.chat.domain.communication.service;

import com.shinhan.heybob.chat.domain.communication.dto.ServerMessage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface MainServerCommunicationService {
    
    // 기본 메시지 전송
    void sendMessage(ServerMessage message);
    
    // 응답 대기 메시지 전송 (타임아웃 포함)
    CompletableFuture<ServerMessage> sendMessageWithResponse(ServerMessage message, long timeoutMs);
    
    // === 채팅방 관련 ===
    void createRoom(String mealAppointmentId, String creatorUserId, String roomName, List<String> initialMembers);
    CompletableFuture<ServerMessage> joinRoom(String roomId, String userId, String userName, String studentId);
    CompletableFuture<List<Map<String, Object>>> getRoomMembers(String roomId, String requesterId);
    
    // === 정산 관련 ===
    CompletableFuture<ServerMessage> processSettlement(String settlementId, String roomId,
                                                       List<String> acceptedUsers, Integer perPersonAmount,
                                                       String note, String requesterId);
    
    // === 사용자 권한 ===
    CompletableFuture<Boolean> validateUserAccess(String userId, String roomId);
    
    // === 정산 응답 전송 ===
    void sendSettlementResponse(String settlementId, String userId, String userName, 
                               String response, String responseTime);
    
    // === 유틸리티 ===
    void sendHeartbeat();
    boolean isMainServerHealthy();
}