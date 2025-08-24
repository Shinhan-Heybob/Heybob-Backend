package com.shinhan.heybob.domain.user.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.user.dto.UserResponseDto;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser1;
    private User testUser2;
    private User testUser3;

    @BeforeEach
    void setUp() {
        testUser1 = User.builder()
                .id(1L)
                .name("김철수")
                .studentId("2020001")
                .university("신한대학교")
                .department("컴퓨터공학과")
                .profileUrl("profile1.jpg")
                .password("password")
                .build();

        testUser2 = User.builder()
                .id(2L)
                .name("이영희")
                .studentId("2020002")
                .university("신한대학교")
                .department("경영학과")
                .profileUrl("profile2.jpg")
                .password("password")
                .build();

        testUser3 = User.builder()
                .id(3L)
                .name("박민수")
                .studentId("2020003")
                .university("신한대학교")
                .department("전자공학과")
                .profileUrl("profile3.jpg")
                .password("password")
                .build();
    }

    @Test
    @DisplayName("사용자 검색 - 이름으로 검색")
    void searchUsers_ByName() {
        String keyword = "김";
        given(userRepository.searchUsers(keyword))
                .willReturn(Arrays.asList(testUser1));

        List<UserResponseDto> result = userService.searchUsers(keyword);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("김철수");
        verify(userRepository).searchUsers(keyword);
    }

    @Test
    @DisplayName("사용자 검색 - 학번으로 검색")
    void searchUsers_ByStudentId() {
        String keyword = "2020002";
        given(userRepository.searchUsers(keyword))
                .willReturn(Arrays.asList(testUser2));

        List<UserResponseDto> result = userService.searchUsers(keyword);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStudentId()).isEqualTo("2020002");
        assertThat(result.get(0).getName()).isEqualTo("이영희");
    }

    @Test
    @DisplayName("사용자 검색 - 학과로 검색")
    void searchUsers_ByDepartment() {
        String keyword = "공학";
        given(userRepository.searchUsers(keyword))
                .willReturn(Arrays.asList(testUser1, testUser3));

        List<UserResponseDto> result = userService.searchUsers(keyword);

        assertThat(result).hasSize(2);
        assertThat(result).extracting("department")
                .containsExactlyInAnyOrder("컴퓨터공학과", "전자공학과");
    }

    @Test
    @DisplayName("사용자 검색 - 결과가 없는 경우")
    void searchUsers_NoResult() {
        String keyword = "없는사용자";
        given(userRepository.searchUsers(keyword))
                .willReturn(Collections.emptyList());

        List<UserResponseDto> result = userService.searchUsers(keyword);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("사용자 검색 - 빈 키워드")
    void searchUsers_EmptyKeyword() {
        String keyword = "";

        assertThatThrownBy(() -> userService.searchUsers(keyword))
                .isInstanceOf(HeybobException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("사용자 검색 - null 키워드")
    void searchUsers_NullKeyword() {
        assertThatThrownBy(() -> userService.searchUsers(null))
                .isInstanceOf(HeybobException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("사용자 검색 - 공백만 있는 키워드")
    void searchUsers_WhitespaceKeyword() {
        String keyword = "   ";

        assertThatThrownBy(() -> userService.searchUsers(keyword))
                .isInstanceOf(HeybobException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("사용자 ID로 조회 - 정상 케이스")
    void getUserById_Success() {
        Long userId = 1L;
        given(userRepository.findById(userId))
                .willReturn(Optional.of(testUser1));

        UserResponseDto result = userService.getUserById(userId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("김철수");
        assertThat(result.getStudentId()).isEqualTo("2020001");
        assertThat(result.getDepartment()).isEqualTo("컴퓨터공학과");
    }

    @Test
    @DisplayName("사용자 ID로 조회 - 존재하지 않는 사용자")
    void getUserById_NotFound() {
        Long userId = 999L;
        given(userRepository.findById(userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(userId))
                .isInstanceOf(HeybobException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("프로필 URL 업데이트 - 정상 케이스")
    void updateProfileUrl_Success() {
        Long userId = 1L;
        String newProfileUrl = "new-profile.jpg";
        
        given(userRepository.findById(userId))
                .willReturn(Optional.of(testUser1));

        userService.updateProfileUrl(userId, newProfileUrl);

        verify(userRepository).findById(userId);
        assertThat(testUser1.getProfileUrl()).isEqualTo(newProfileUrl);
    }

    @Test
    @DisplayName("프로필 URL 업데이트 - 존재하지 않는 사용자")
    void updateProfileUrl_UserNotFound() {
        Long userId = 999L;
        String newProfileUrl = "new-profile.jpg";
        
        given(userRepository.findById(userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfileUrl(userId, newProfileUrl))
                .isInstanceOf(HeybobException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.USER_NOT_FOUND);
    }
}