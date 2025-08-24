package com.shinhan.heybob.chat.global.error;

import lombok.Getter;

@Getter
public class ChatException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    public ChatException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public ChatException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public ChatException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}