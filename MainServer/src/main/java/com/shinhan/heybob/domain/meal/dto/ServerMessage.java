package com.shinhan.heybob.domain.meal.dto;

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
        CREATE_ROOM,
        JOIN_ROOM,
        LEAVE_ROOM,
        ROOM_CREATED,
        ROOM_JOINED,
        ROOM_LEFT,
        VALIDATE_ACCESS,
        ACCESS_VALIDATED,
        GET_ROOM_MEMBERS,
        ROOM_MEMBERS_RESPONSE,
        ERROR
    }
}