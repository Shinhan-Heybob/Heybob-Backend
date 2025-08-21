package com.shinhan.heybob.domain.auth.service;

import com.shinhan.heybob.domain.auth.dto.AuthResponseDto;
import com.shinhan.heybob.domain.auth.dto.RefreshTokenResponseDto;
import com.shinhan.heybob.domain.auth.dto.UserLoginRequestDto;
import com.shinhan.heybob.domain.user.dto.UserCreateRequestDto;
import com.shinhan.heybob.domain.user.dto.UserResponseDto;

public interface AuthService {

    RefreshTokenResponseDto createAccessToken(String refreshToken);

    RefreshTokenResponseDto createAccessTokenByHeader(String authorizationHeader);

    UserResponseDto signup(UserCreateRequestDto userCreateRequestDto);

    AuthResponseDto login(UserLoginRequestDto userLoginRequestDto);
}
