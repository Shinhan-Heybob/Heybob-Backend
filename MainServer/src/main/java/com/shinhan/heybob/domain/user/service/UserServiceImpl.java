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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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

    @Transactional
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

    @Transactional
    @Override
    public UserResponseDto getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));
        
        return new UserResponseDto(user);
    }

    @Transactional
    @Override
    public List<UserResponseDto> getUsersByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();

        // null 제거 + 중복 제거
        List<Long> distinct = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<User> found = userRepository.findAllById(distinct);

        // 누락 검증
        Set<Long> foundIds = found.stream().map(User::getId).collect(Collectors.toSet());
        List<Long> missing = distinct.stream().filter(id -> !foundIds.contains(id)).toList();
        if (!missing.isEmpty()) {
            // 필요에 따라 예외 종류 변경
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        Map<Long, User> byId = found.stream().collect(Collectors.toMap(User::getId, Function.identity()));

        return userIds.stream()
                .map(byId::get)
                .map(UserResponseDto::new)
                .toList();
    }
}
