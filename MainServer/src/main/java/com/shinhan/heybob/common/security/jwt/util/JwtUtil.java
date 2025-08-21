package com.shinhan.heybob.common.security.jwt.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-ttl}")
    private long accessTokenValiditySeconds;

    @Value("${jwt.refresh-ttl}")
    private long refreshTokenValiditySeconds;

    @Value("${jwt.token-prefix}")
    private String tokenPrefix;

    @Value("${jwt.header}")
    private String header;

    private SecretKey secretKey;

    private SecretKey getSecretKey() {
        if (secretKey == null) {
            secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        return secretKey;
    }

}

