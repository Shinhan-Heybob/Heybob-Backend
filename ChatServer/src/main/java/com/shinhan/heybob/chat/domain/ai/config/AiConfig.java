package com.shinhan.heybob.chat.domain.ai.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@ComponentScan(basePackages = "com.shinhan.heybob.chat.domain.ai")
public class AiConfig {
    // AI 관련 설정을 명시적으로 스캔
}