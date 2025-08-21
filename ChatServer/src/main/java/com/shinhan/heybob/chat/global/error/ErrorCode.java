package com.shinhan.heybob.chat.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    
    // 일반 에러
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT_001", "서버 내부 오류가 발생했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "CHAT_002", "잘못된 요청입니다."),
    
    // 인증/인가 에러
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "CHAT_100", "인증이 필요합니다."),
    USER_INFO_MISSING(HttpStatus.BAD_REQUEST, "CHAT_101", "사용자 정보가 누락되었습니다."),
    
    // 채팅 관련 에러
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_200", "채팅방을 찾을 수 없습니다."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_201", "메시지를 찾을 수 없습니다."),
    MESSAGE_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT_202", "메시지 저장에 실패했습니다."),
    INVALID_MESSAGE_TYPE(HttpStatus.BAD_REQUEST, "CHAT_203", "지원하지 않는 메시지 타입입니다."),
    
    // Redis 관련 에러
    REDIS_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT_300", "Redis 연결 오류가 발생했습니다."),
    REDIS_STREAM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT_301", "Redis Stream 처리 중 오류가 발생했습니다."),
    
    // MongoDB 관련 에러
    MONGODB_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT_400", "MongoDB 연결 오류가 발생했습니다."),
    MONGODB_QUERY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT_401", "데이터베이스 조회 중 오류가 발생했습니다.");
    
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}