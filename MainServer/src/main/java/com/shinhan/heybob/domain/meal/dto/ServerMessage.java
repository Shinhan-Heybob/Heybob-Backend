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
        // Chat → Main 요청
        CREATE_ROOM,           // 채팅방 생성 요청
        JOIN_ROOM,             // 채팅방 입장 요청  
        LEAVE_ROOM,            // 채팅방 퇴장 요청
        GET_ROOM_MEMBERS,      // 채팅방 멤버 조회
        PROCESS_SETTLEMENT,    // 정산 처리 요청
        VALIDATE_ACCESS,       // 사용자 접근 권한 확인
        
        // Main → Chat 응답
        ROOM_CREATED,          // 채팅방 생성 완료
        ROOM_JOINED,           // 채팅방 입장 완료
        ROOM_LEFT,             // 채팅방 퇴장 완료
        ACCESS_VALIDATED,      // 접근 권한 확인 완료
        ROOM_MEMBERS_RESPONSE, // 채팅방 멤버 정보
        SETTLEMENT_PROCESSED,  // 정산 처리 완료
        
        // Main → Chat 알림
        ROOM_STATUS_CHANGED,   // 채팅방 상태 변경
        MEMBER_JOINED,         // 새 멤버 입장 알림
        MEMBER_LEFT,           // 멤버 퇴장 알림
        SETTLEMENT_COMPLETED,  // 정산 완료 알림
        
        // 정산 관련 추가
        BROADCAST_SETTLEMENT_REQUEST, // Main이 Chat에게 정산 메시지 브로드캐스트 요청
        SETTLEMENT_RESPONSE,          // Chat이 Main에게 사용자 정산 응답 전달
        
        // 공통
        ERROR,                 // 일반 에러
        ERROR_RESPONSE,        // 에러 응답
        HEARTBEAT             // 헬스체크
    }
}