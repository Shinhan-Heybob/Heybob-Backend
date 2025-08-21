package com.shinhan.heybob.domain.auth.service;

import com.shinhan.heybob.domain.auth.dto.RefreshTokenResponseDto;

public interface AuthService {

    RefreshTokenResponseDto createAccessToken(String refreshToken);

    RefreshTokenResponseDto createAccessTokenByHeader(String authorizationHeader);
}
