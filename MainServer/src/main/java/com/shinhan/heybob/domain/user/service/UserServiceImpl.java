package com.shinhan.heybob.domain.user.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.user.dto.UserResponseDto;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Transactional
    @Override
    public void updateProfileUrl(Long userId, String newProfileUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

        user.updateProfileUrl(newProfileUrl);

        log.info("사용자 프로필 이미지 변경 완료");
    }

    @Override
    public List<UserResponseDto> searchUsers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }
        
        List<User> users = userRepository.searchUsers(keyword.trim());
        
        return users.stream()
                .map(UserResponseDto::new)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponseDto getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));
        
        return new UserResponseDto(user);
    }
}
