package com.shinhan.heybob.chat.domain.communication.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ServerMessage {
    private String messageId;          // 고유 메시지 ID
    private String correlationId;      // 요청-응답 연결용 ID
    private MessageType messageType;   // 메시지 타입
    private String sourceServer;       // 발신 서버 (CHAT, MAIN)
    private String targetServer;       // 수신 서버 (CHAT, MAIN)
    private LocalDateTime timestamp;   // 발송 시간
    private Map<String, Object> payload;  // 실제 데이터
    private Integer retryCount;        // 재시도 횟수
    private LocalDateTime expiryTime;  // 만료 시간
    
    public enum MessageType {
        // Chat → Main 요청
        CREATE_ROOM,           // 채팅방 생성 요청
        JOIN_ROOM,             // 채팅방 입장 요청  
        GET_ROOM_MEMBERS,      // 채팅방 멤버 조회
        PROCESS_SETTLEMENT,    // 정산 처리 요청
        VALIDATE_USER_ACCESS,  // 사용자 접근 권한 확인
        
        // Main → Chat 응답
        ROOM_CREATED,          // 채팅방 생성 완료
        ROOM_JOINED,           // 채팅방 입장 완료
        ROOM_MEMBERS_RESPONSE, // 채팅방 멤버 정보
        SETTLEMENT_PROCESSED,  // 정산 처리 완료
        USER_ACCESS_RESPONSE,  // 사용자 접근 권한 응답
        
        // Main → Chat 알림
        ROOM_STATUS_CHANGED,   // 채팅방 상태 변경
        MEMBER_JOINED,         // 새 멤버 입장 알림
        MEMBER_LEFT,           // 멤버 퇴장 알림
        SETTLEMENT_COMPLETED,  // 정산 완료 알림
        
        // 정산 관련 추가
        BROADCAST_SETTLEMENT_REQUEST, // Main이 Chat에게 정산 메시지 브로드캐스트 요청
        SETTLEMENT_RESPONSE,          // Chat이 Main에게 사용자 정산 응답 전달
        
        // 공통
        ERROR_RESPONSE,        // 에러 응답
        HEARTBEAT             // 헬스체크
    }
}