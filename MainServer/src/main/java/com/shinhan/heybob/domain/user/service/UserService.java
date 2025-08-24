package com.shinhan.heybob.domain.user.service;

import com.shinhan.heybob.domain.user.dto.UserResponseDto;

import java.util.List;

public interface UserService {

    void updateProfileUrl(Long userId, String newProfileUrl);
    
    List<UserResponseDto> searchUsers(String keyword);
    
    UserResponseDto getUserById(Long userId);

}
