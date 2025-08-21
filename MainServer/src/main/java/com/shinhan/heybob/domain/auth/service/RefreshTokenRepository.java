package com.shinhan.heybob.domain.auth.service;

import com.shinhan.heybob.domain.auth.entity.RefreshToken;
import org.springframework.data.repository.Repository;

interface RefreshTokenRepository extends Repository<RefreshToken, Long> {
}
