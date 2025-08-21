package com.shinhan.heybob.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExceptionStatus {

    // JWT
    UN_AUTHENTICATION_TOKEN(HttpStatus.UNAUTHORIZED, 401, "토큰 정보가 없습니다."),
    INVALID_TOKEN(HttpStatus.FORBIDDEN, 403, "유효하지 않은 토큰입니다."),
    INVALID_AES_KEY_LENGTH(HttpStatus.UNPROCESSABLE_ENTITY, 422, "암호화 키 바이트가 올바르지 못합니다.");

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
