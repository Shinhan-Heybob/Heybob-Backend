package com.shinhan.heybob.common.security.filter;

import com.shinhan.heybob.common.security.jwt.util.JwtUtil;
import com.shinhan.heybob.domain.user.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = jwtUtil.resolveToken(request);

            if (token != null && jwtUtil.validateAccessToken(token)) {
                Long userId = jwtUtil.getUserIdFromAccessToken(token);
                UserDetails userDetails = userDetailsService.loadUserById(userId);

                if (userDetails != null) {
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                } else {
                    log.error("토큰에서 추출된 userId으로 user를 찾지 못함: " + userId);
                    SecurityContextHolder.clearContext(); // 인증 비우기
                }
            }
        } catch (Exception e) {
            log.error("사용자 인증을 설정하지 못함: " + e.getMessage());

            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

}
