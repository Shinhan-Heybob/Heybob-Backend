package com.shinhan.heybob.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthLoginResponseDto {

    private Long userId;
    private String accessToken;
    private String refreshToken;
}
