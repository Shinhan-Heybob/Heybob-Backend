package com.shinhan.heybob.common.security.jwt.util;

import com.shinhan.heybob.common.user.UserPrincipalDetails;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SecurityException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

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

    public String generateAccessToken(UserPrincipalDetails userPrincipalDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenValiditySeconds * 1000); // ms로 넣어야 함

        return Jwts.builder()
                .setSubject(userPrincipalDetails.getUsername())
                .claim("email", userPrincipalDetails.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSecretKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String generateRefreshToken(UserPrincipalDetails userPrincipalDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenValiditySeconds * 1000);

        return Jwts.builder()
                .setSubject(userPrincipalDetails.getUsername())
                .claim("email", userPrincipalDetails.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSecretKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String getUsernameFromAccessToken(String token) {
        return extractUsername(token);
    }

    public String getUsernameFromRefreshToken(String token) {
        return extractUsername(token);
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload().getSubject();

    }

    public boolean validateAccessToken(String token) {
        return validateToken(token);
    }

    public boolean validateRefreshToken(String token) {
        if (token == null || token.isEmpty()) return false;
        return validateToken(token);
    }

    private boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("유효하지 않은 JWT signature: " + e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("만료된 JWT 토큰: " + e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("지원하지 않은 JWT 토큰: " +  e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims이 비었습니다: " + e.getMessage());
        }
        return false;
    }

    public String resolveToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(header);
        if (authorizationHeader != null && authorizationHeader.startsWith(tokenPrefix)) {
            return authorizationHeader.substring(tokenPrefix.length());
        }
        return null;
    }
}

