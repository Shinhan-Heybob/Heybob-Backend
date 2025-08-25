package com.shinhan.heybob.common.security.config;

import com.shinhan.heybob.common.security.filter.TokenAuthenticationFilter;
import com.shinhan.heybob.common.security.jwt.util.JwtUtil;
import com.shinhan.heybob.domain.auth.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsConfig corsConfig;
    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))
                .sessionManagement(sessionManagement
                        -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .requestMatchers("/auth/**").permitAll()
                                .requestMatchers("/test/**").permitAll()  // context-path 제거
                                .requestMatchers("/actuator/health").permitAll()
                                .anyRequest().authenticated()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            try {
                                String token = jwtUtil.resolveToken(request);
                                if (token != null && jwtUtil.validateAccessToken(token)) {
                                    Long userId = jwtUtil.getUserIdFromAccessToken(token);

                                    // 로그아웃 로직
                                    refreshTokenRepository.findById(userId)
                                            .ifPresent(refreshToken -> {
                                                refreshToken.updateToken("");
                                                refreshTokenRepository.save(refreshToken);
                                            });
                                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                                    return;
                                }

                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            } catch (Exception e) {
                                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            }
                        })
                )
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, ex1) -> {
                            // 인증 안된 사용자가 보호 API 접근: 401
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        })
                        .accessDeniedHandler((req, res, ex2) -> {
                            // 권한 부족: 403
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        })
                );

        return http.build();
    }

}
