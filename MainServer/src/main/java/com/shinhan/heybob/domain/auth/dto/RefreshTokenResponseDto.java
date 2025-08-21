package com.shinhan.heybob.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RefreshTokenResponseDto {

    private String accessToken;

    private String refreshToken;
}
