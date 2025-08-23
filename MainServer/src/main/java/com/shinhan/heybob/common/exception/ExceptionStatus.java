package com.shinhan.heybob.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExceptionStatus {

    // AUTH
    EMPTY_AGREE_TERMS(HttpStatus.BAD_REQUEST, 400, "개인정보 처리방침에 동의해야 회원가입이 가능합니다."),

    // JWT
    UN_AUTHENTICATION_TOKEN(HttpStatus.UNAUTHORIZED, 401, "토큰 정보가 없습니다."),
    INVALID_TOKEN(HttpStatus.FORBIDDEN, 403, "유효하지 않은 토큰입니다."),
    INVALID_AES_KEY_LENGTH(HttpStatus.UNPROCESSABLE_ENTITY, 422, "암호화 키 바이트가 올바르지 못합니다."),

    // USER
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, 404, "일치하는 유저 정보를 찾을 수 없습니다"),
    STUDENT_ID_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, 400, "이미 존재하는 학번입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, 400, "비밀번호가 유효하지 않습니다."),

    // EXTERNAL FINANCE USER
    FINANCE_API_NOT_FOUND(HttpStatus.BAD_REQUEST, 400, "금융 API 연결 실패했습니다."),
    EMPTY_USER_KEY(HttpStatus.NOT_FOUND, 404, "userKey가 비어있습니다.");

    private final int status;
    private final int customCode;
    private final String message;
    private final String errorStatus;

    ExceptionStatus(HttpStatus httpStatus, int customCode, String message) {
        this.status = httpStatus.value();
        this.customCode = customCode;
        this.message = message;
        this.errorStatus = httpStatus.getReasonPhrase();
    }
}
