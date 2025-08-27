package com.shinhan.heybob.chat.domain.cafeteria.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CafeteriaService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String CAFETERIA_KEY_PREFIX = "cafeteria:today:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    @PostConstruct
    public void initializeDummyData() {
        try {
            String today = LocalDate.now().format(DATE_FORMATTER);
            String cacheKey = CAFETERIA_KEY_PREFIX + today;
            
            // 오늘 학식 정보가 없으면 더미 데이터 생성
            Boolean exists = redisTemplate.hasKey(cacheKey);
            if (Boolean.FALSE.equals(exists)) {
                String dummyData = createDummyCafeteriaData();
                
                // Redis에 캐싱 (24시간 TTL)
                redisTemplate.opsForValue().set(cacheKey, dummyData, 24, TimeUnit.HOURS);
                log.info("✅ 더미 학식 정보 캐싱 완료: {}", cacheKey);
            } else {
                log.info("📚 기존 학식 정보 캐시 존재: {}", cacheKey);
            }
            
        } catch (Exception e) {
            log.error("❌ 학식 정보 초기화 실패", e);
        }
    }
    
    /**
     * 오늘의 학식 정보 조회
     */
    public String getTodayCafeteriaInfo() {
        try {
            String today = LocalDate.now().format(DATE_FORMATTER);
            String cacheKey = CAFETERIA_KEY_PREFIX + today;
            
            Object cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.info("📚 Redis에서 학식 정보 조회 성공: {}", today);
                return cachedData.toString();
            }
            
            // Redis에 데이터가 없으면 오늘은 학식이 없는 것
            log.info("📚 오늘 학식 정보 없음: {}", today);
            return "오늘은 학식이 없습니다!";
            
        } catch (Exception e) {
            log.error("❌ 학식 정보 조회 실패", e);
            return createErrorMessage();
        }
    }
    
    /**
     * 더미 학식 데이터 생성
     */
    private String createDummyCafeteriaData() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM월 dd일"));
        
        return String.format("""
            🍚 %s 학식 정보
            
            🌅 조식 (07:00 - 09:00)
            • 백미밥
            • 된장찌개
            • 계란후라이
            • 김치
            • 우유 1팩
            
            🌞 중식 (11:30 - 14:00)
            • 백미밥 / 현미밥
            • 김치찌개
            • 돈까스 & 소스
            • 새우튀김 2개
            • 마카로니샐러드
            • 배추김치
            • 미역국
            
            🌙 석식 (17:30 - 19:30)
            • 백미밥 / 흑미밥
            • 부대찌개
            • 치킨너겟 3개
            • 떡볶이
            • 오이무침
            • 깍두기
            • 미소된장국
            
            💰 가격: 조식 3,000원 / 중식 4,500원 / 석식 4,500원
            📍 위치: 학생회관 2층 학생식당
            
            맛있게 드세요! 😊""", today);
    }
    
    /**
     * 에러 메시지 생성
     */
    private String createErrorMessage() {
        return """
            😅 죄송합니다!
            
            현재 학식 정보를 불러올 수 없습니다.
            잠시 후 다시 시도해주세요.
            
            📞 학생식당 문의: 031-123-4567""";
    }
    
    /**
     * 수동으로 학식 정보 업데이트 (관리자용)
     */
    public void updateCafeteriaInfo(String date, String info) {
        try {
            String cacheKey = CAFETERIA_KEY_PREFIX + date;
            redisTemplate.opsForValue().set(cacheKey, info, 24, TimeUnit.HOURS);
            log.info("✅ 학식 정보 수동 업데이트 완료: date={}", date);
        } catch (Exception e) {
            log.error("❌ 학식 정보 업데이트 실패: date={}", date, e);
        }
    }
}