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
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if (uri.equals("/auth") || uri.startsWith("/auth/")) return true;
        if (uri.startsWith("/test/")) return true;  // context-path 제거
        if (uri.startsWith("/actuator/health")) return true;  // 헬스체크 제외
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        final String uri = req.getRequestURI();
        final String token = jwtUtil.resolveToken(req);
        log.debug("[JWT] uri={} tokenPresent={}", uri, token != null);

        try {
            // 토큰이 없으면 익명으로 통과
            if (token == null || token.isBlank()) {
                chain.doFilter(req, res);
                return;
            }

            // 토큰이 있으면만 검증하고, 유효하면 컨텍스트에 인증 주입
            if (jwtUtil.validateAccessToken(token)) {
                Long userId = jwtUtil.getUserIdFromAccessToken(token);
                UserDetails user = userDetailsService.loadUserById(userId);
                if (user != null) {
                    var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    // 유저 없으면 인증 세팅 없이 그대로 통과
                    SecurityContextHolder.clearContext();
                }
            } else {
                // 무효 토큰이어도 여기서 401 쓰지 말고 통과 → 보호자원에서만 401
                SecurityContextHolder.clearContext();
            }
        } catch (Exception e) {
            log.error("[JWT] filter error on {}: {}", uri, e.getMessage(), e);
            SecurityContextHolder.clearContext();
            // 여기서 응답 종료/401 금지
        }
        chain.doFilter(req, res);
    }
}
