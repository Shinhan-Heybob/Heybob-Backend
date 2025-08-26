package com.shinhan.heybob.chat.domain.communication.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PayloadBuilder {
    
    // 채팅방 생성 요청 페이로드
    public static Map<String, Object> createRoomPayload(String mealAppointmentId, String creatorUserId, 
                                                        String roomName, List<String> initialMembers) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("mealAppointmentId", mealAppointmentId);
        payload.put("creatorUserId", creatorUserId);
        payload.put("roomName", roomName);
        payload.put("initialMembers", initialMembers);
        return payload;
    }
    
    // 채팅방 입장 요청 페이로드
    public static Map<String, Object> joinRoomPayload(String roomId, String userId, 
                                                      String userName, String studentId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("roomId", roomId);
        payload.put("userId", userId);
        payload.put("userName", userName);
        payload.put("studentId", studentId);
        return payload;
    }
    
    // 채팅방 멤버 조회 요청 페이로드
    public static Map<String, Object> getRoomMembersPayload(String roomId, String requesterId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("roomId", roomId);
        payload.put("requesterId", requesterId);
        return payload;
    }
    
    // 정산 처리 요청 페이로드
    public static Map<String, Object> processSettlementPayload(String settlementId, String roomId,
                                                              List<String> acceptedUsers, Integer perPersonAmount,
                                                              String note, String requesterId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("settlementId", settlementId);
        payload.put("roomId", roomId);
        payload.put("acceptedUsers", acceptedUsers);
        payload.put("perPersonAmount", perPersonAmount);
        payload.put("note", note);
        payload.put("requesterId", requesterId);
        return payload;
    }
    
    // 사용자 접근 권한 확인 페이로드
    public static Map<String, Object> validateUserAccessPayload(String userId, String roomId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("roomId", roomId);
        return payload;
    }
    
    // 채팅방 멤버 정보 응답 페이로드
    public static Map<String, Object> roomMembersResponsePayload(String roomId, List<Map<String, Object>> members,
                                                                String roomStatus, String mealAppointmentId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("roomId", roomId);
        payload.put("members", members);
        payload.put("roomStatus", roomStatus);
        payload.put("mealAppointmentId", mealAppointmentId);
        return payload;
    }
    
    // 정산 처리 완료 응답 페이로드
    public static Map<String, Object> settlementProcessedPayload(String settlementId, String status,
                                                               List<Map<String, Object>> paymentResults,
                                                               String totalAmount, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("settlementId", settlementId);
        payload.put("status", status); // SUCCESS, FAILED, PARTIAL
        payload.put("paymentResults", paymentResults);
        payload.put("totalAmount", totalAmount);
        payload.put("message", message);
        return payload;
    }
    
    // 정산 브로드캐스트 요청 페이로드 (Main → Chat)
    public static Map<String, Object> broadcastSettlementPayload(String settlementId, String roomId,
                                                                String requesterId, String requesterName,
                                                                List<String> targetUserIds, Integer perPersonAmount,
                                                                String note, String expiryTime) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("settlementId", settlementId);
        payload.put("roomId", roomId);
        payload.put("requesterId", requesterId);
        payload.put("requesterName", requesterName);
        payload.put("targetUserIds", targetUserIds);
        payload.put("perPersonAmount", perPersonAmount);
        payload.put("note", note);
        payload.put("expiryTime", expiryTime);
        return payload;
    }
    
    // 정산 응답 페이로드 (Chat → Main)  
    public static Map<String, Object> settlementResponsePayload(String settlementId, String userId,
                                                              String userName, String response, String responseTime) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("settlementId", settlementId);
        payload.put("userId", userId);
        payload.put("userName", userName);
        payload.put("response", response); // "ACCEPT", "REJECT", "CANCEL"
        payload.put("responseTime", responseTime);
        return payload;
    }

    // 에러 응답 페이로드
    public static Map<String, Object> errorResponsePayload(String errorCode, String errorMessage, 
                                                          String originalMessageType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("errorCode", errorCode);
        payload.put("errorMessage", errorMessage);
        payload.put("originalMessageType", originalMessageType);
        return payload;
    }
}