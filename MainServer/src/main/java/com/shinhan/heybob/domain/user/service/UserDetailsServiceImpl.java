package com.shinhan.heybob.domain.user.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.common.user.UserPrincipalDetails;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String principal) throws UsernameNotFoundException {
        User user = findByPrincipal(principal);
        return toPrincipal(user);
    }

    public UserDetails loadUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));
        return toPrincipal(user);
    }

    private User findByPrincipal(String principal) {
        if (principal != null && principal.chars().allMatch(Character::isDigit)) {
            Long id = Long.parseLong(principal);
            return userRepository.findById(id)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found by id: " + id));
        }

        throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
    }

    private UserDetails toPrincipal(User user) {
        List<GrantedAuthority> authorities =
                Collections.singletonList((GrantedAuthority) () -> "ROLE_USER");
        return new UserPrincipalDetails(user, authorities);
    }
}
