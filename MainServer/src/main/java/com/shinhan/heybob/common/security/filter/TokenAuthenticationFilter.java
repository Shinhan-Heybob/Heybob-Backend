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

        String uri = req.getRequestURI();
        String token = jwtUtil.resolveToken(req);

        try {
            if (token != null && !token.isBlank() && jwtUtil.validateAccessToken(token)) {
                Long userId = jwtUtil.getUserIdFromAccessToken(token);
                UserDetails user = userDetailsService.loadUserById(userId);
                if (user != null) {
                    var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } else {
                SecurityContextHolder.clearContext(); // 토큰 없음/무효 시 익명 통과
            }
        } catch (Exception e) {
            log.error("[JWT] token process error on {}: {}", uri, e.getMessage(), e);
            SecurityContextHolder.clearContext();
        }

        // ✅ 항상 체인은 바깥에서 호출
        chain.doFilter(req, res);
    }
}
