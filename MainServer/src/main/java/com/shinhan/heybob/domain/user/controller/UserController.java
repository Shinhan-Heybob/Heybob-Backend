package com.shinhan.heybob.domain.user.controller;

import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.user.dto.UserResponseDto;
import com.shinhan.heybob.domain.user.dto.UserUpdateProfileUrlRequestDto;
import com.shinhan.heybob.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/search")
    public ResponseEntity<List<UserResponseDto>> searchUsers(
            @RequestParam String keyword) {
        List<UserResponseDto> users = userService.searchUsers(keyword);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDto> getUserById(
            @PathVariable Long userId) {
        UserResponseDto user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }

}
