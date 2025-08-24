package com.shinhan.heybob.domain.user.controller;

import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.user.dto.UserUpdateProfileUrlRequestDto;
import com.shinhan.heybob.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/update-profile")
    public ResponseEntity<Void> updateProfileUrl(
            @AuthenticationPrincipal UserPrincipalDetails userPrincipal,
            @RequestBody UserUpdateProfileUrlRequestDto requestDto
            ) {
        Long userId = userPrincipal.getUserId();
        userService.updateProfileUrl(userId, requestDto.getNewProfileUrl());
        return ResponseEntity.noContent().build();
    }

}
