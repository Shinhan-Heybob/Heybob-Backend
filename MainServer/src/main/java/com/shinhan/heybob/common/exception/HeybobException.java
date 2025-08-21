package com.shinhan.heybob.common.exception;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HeybobException extends RuntimeException {

    private final ExceptionStatus exceptionStatus;

    public ExceptionStatus getExceptionStatus() {
        return exceptionStatus;
    }

    @Override
    public String getMessage() {
        return exceptionStatus.getMessage();
    }

    public int getStatus() {
        return exceptionStatus.getStatus();
    }
}
