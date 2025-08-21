package com.shinhan.heybob.common.security.config;

import com.shinhan.heybob.common.security.filter.TokenAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsConfig corsConfig;
    private final TokenAuthenticationFilter tokenAuthenticationFilter;

}
