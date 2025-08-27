package com.shinhan.heybob.domain.auth.controller;

import com.shinhan.heybob.domain.auth.dto.AuthLoginResponseDto;
import com.shinhan.heybob.domain.auth.dto.AuthResponseDto;
import com.shinhan.heybob.domain.auth.dto.UserLoginRequestDto;
import com.shinhan.heybob.domain.auth.service.AuthService;
import com.shinhan.heybob.domain.user.dto.UserCreateRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private static final String HEADER_AUTH = "Authorization";
    private static final String TOKEN_TYPE = "Bearer ";

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody UserCreateRequestDto userCreateRequestDto) {
        authService.signup(userCreateRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Signup Successful");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponseDto> login(@Valid @RequestBody UserLoginRequestDto userLoginRequestDto) {
        AuthResponseDto authResponseDto = authService.login(userLoginRequestDto);

        return ResponseEntity.ok()
                .header(HEADER_AUTH, TOKEN_TYPE + authResponseDto.getAccessToken())
                .body(AuthLoginResponseDto.builder()
                        .userId(authResponseDto.getUserId())
                        .refreshToken(authResponseDto.getRefreshToken())
                        .build());
    }

}
