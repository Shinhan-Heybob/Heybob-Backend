package com.shinhan.heybob.chat.global.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestConfig {
    // 테스트 환경에서만 활성화되는 설정
}