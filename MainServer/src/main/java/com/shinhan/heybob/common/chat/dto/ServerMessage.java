package com.shinhan.heybob.common.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerMessage {
    
    private String messageId;
    private MessageType messageType;
    private String sourceServer;
    private String targetServer;
    private LocalDateTime timestamp;
    private Map<String, Object> payload;
    private int retryCount;
    private LocalDateTime expiryTime;
    
    public enum MessageType {
        // 채팅방 관리
        CREATE_ROOM,
        ROOM_CREATED, // 지금 안 씀 ❌
        DELETE_ROOM, // 지금 안 씀 ❌
        ROOM_DELETED, // 지금 안 씀 ❌
        
        // 사용자 관리
        ADD_USER, // 지금 안 씀 ❌
        USER_ADDED, // 지금 안 씀 ❌
        REMOVE_USER, // 지금 안 씀 ❌
        USER_REMOVED, // 지금 안 씀 ❌
        
        // 금융 관련
        PAYMENT_REQUEST, // 정산 요청
        PAYMENT_COMPLETE, // 정산 완료
        SAVINGS_REQUEST, // 적금 요청
        SAVINGS_COMPLETE, // 적금 완료

        // 시스템 메시지
        SYSTEM_MESSAGE,
        ERROR,
        ACKNOWLEDGMENT
    }
}