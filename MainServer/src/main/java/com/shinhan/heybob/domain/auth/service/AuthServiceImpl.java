package com.shinhan.heybob.domain.auth.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.common.security.jwt.util.JwtUtil;
import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.auth.dto.AuthResponseDto;
import com.shinhan.heybob.domain.auth.dto.RefreshTokenResponseDto;
import com.shinhan.heybob.domain.auth.dto.UserLoginRequestDto;
import com.shinhan.heybob.domain.auth.entity.RefreshToken;
import com.shinhan.heybob.domain.auth.repository.RefreshTokenRepository;
import com.shinhan.heybob.domain.financePersonal.entity.ExternalFinanceUser;
import com.shinhan.heybob.domain.financePersonal.service.ExternalFinanceUserService;
import com.shinhan.heybob.domain.financePersonal.service.FinanceAccountService;
import com.shinhan.heybob.domain.user.dto.UserCreateRequestDto;
import com.shinhan.heybob.domain.user.dto.UserResponseDto;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import com.shinhan.heybob.domain.user.service.UserDetailsServiceImpl;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final ExternalFinanceUserService externalFinanceUserService;
    private final FinanceAccountService financeAccountService;

    @Transactional
    @Override
    public RefreshTokenResponseDto createAccessToken(String refreshToken) {
        jwtUtil.validateRefreshToken(refreshToken);

        Long userId = jwtUtil.getUserIdFromRefreshToken(refreshToken);
        RefreshToken storedToken = findRefreshToken(userId);

        if (!storedToken.getToken().equals(refreshToken)) {
            throw new HeybobException(ExceptionStatus.INVALID_TOKEN);
        }

        UserDetails userDetails = userDetailsService.loadUserById(userId);
        validateUserDetails(userDetails);

        String newAccessToken = jwtUtil.generateAccessToken((UserPrincipalDetails) userDetails);
        String newRefreshToken = rotateRefreshToken(userDetails, storedToken);

        return new RefreshTokenResponseDto(newAccessToken, newRefreshToken);
    }

    @Transactional
    @Override
    public RefreshTokenResponseDto createAccessTokenByHeader(String authorizationHeader) {
        String refreshToken = extractToken(authorizationHeader);
        return createAccessToken(refreshToken);
    }

    @Override
    public UserResponseDto signup(UserCreateRequestDto userCreateRequestDto) {
        log.info("User Create RequestDto: {}", userCreateRequestDto);

        if (Boolean.FALSE.equals(userCreateRequestDto.getAgreeTerms())) {
            throw new HeybobException(ExceptionStatus.EMPTY_AGREE_TERMS);
        }

        verifyExistUser(userCreateRequestDto);

        String encryptedPassword = passwordEncoder.encode(userCreateRequestDto.getPassword());

        User createdUser = userCreateRequestDto.toEntity(userCreateRequestDto, encryptedPassword);

        userRepository.save(createdUser);

        log.info("[User] 사용자 생성 완료");

        // ExternalFinanceUser 생성, userId(이메일 형식), userKey 발급
        ExternalFinanceUser externalFinanceUser = externalFinanceUserService.createUserKey(createdUser.getId());
        Long externalFinanceUserId = externalFinanceUser.getId();
        String userKey = externalFinanceUser.getUserKey();

        log.info("[ExternalFinanceUserService] userKey 생성 완료");

        // userKey로 계좌 생성
        financeAccountService.createDemandDepositAccount(externalFinanceUserId, userKey);

        log.info("[FinanceAccountService] userKey로 계좌 생성 완료");

        return new UserResponseDto(createdUser);
    }

    @Override
    public AuthResponseDto login(UserLoginRequestDto userLoginRequestDto) {
        User user = userRepository.findByUniversityAndStudentId(
                        userLoginRequestDto.getUniversity(), userLoginRequestDto.getStudentId())
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

        if (!passwordEncoder.matches(userLoginRequestDto.getPassword(), user.getPassword())) {
            throw new HeybobException(ExceptionStatus.INVALID_PASSWORD);
        }

        UserDetails userDetails = userDetailsService.loadUserById(user.getId());

        String accessToken = jwtUtil.generateAccessToken((UserPrincipalDetails) userDetails);
        String refreshToken = jwtUtil.generateRefreshToken((UserPrincipalDetails) userDetails);

        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional
    public String rotateRefreshToken(UserDetails userDetails, RefreshToken refreshToken) {
        String newRefreshToken = jwtUtil.generateRefreshToken((UserPrincipalDetails) userDetails);
        refreshToken.updateToken(newRefreshToken);
        refreshTokenRepository.save(refreshToken);
        return newRefreshToken;
    }

    private RefreshToken findRefreshToken(Long userId) {
        return refreshTokenRepository.findById(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.UN_AUTHENTICATION_TOKEN));
    }

    private void validateUserDetails(UserDetails userDetails) {
        if (userDetails == null) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring("Bearer ".length());
        }
        throw new HeybobException(ExceptionStatus.INVALID_TOKEN);
    }

    private void verifyExistUser(UserCreateRequestDto userCreateRequestDto) {
        if (userRepository.existsByStudentIdAndUniversity(
                userCreateRequestDto.getStudentId(),
                userCreateRequestDto.getUniversity())) {
            throw new HeybobException(ExceptionStatus.STUDENT_ID_ALREADY_EXISTS);
        }
    }

}
