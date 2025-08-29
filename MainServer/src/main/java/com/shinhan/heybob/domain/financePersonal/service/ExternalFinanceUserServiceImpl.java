package com.shinhan.heybob.domain.financePersonal.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.financePersonal.entity.ExternalFinanceUser;
import com.shinhan.heybob.domain.financePersonal.repository.ExternalFinanceUserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalFinanceUserServiceImpl implements ExternalFinanceUserService {

    private static final String EMAIL_DOMAIN = "@ssafy.com";
    private static final int MAX_EMAIL_LEN = 40; // 외부 문서 기준에 맞춰 조정
    private static final int MAX_LOCAL_LEN = MAX_EMAIL_LEN - EMAIL_DOMAIN.length();
    private static final int MAX_RETRY = 10;
    private static final String USER_KEY_END_POINT = "/member/";

    private final ExternalFinanceUserRepository externalFinanceUserRepository;
    private final RestTemplate restTemplate;
    private final SecureRandom rng = new SecureRandom();

    @Value("${ssafy.finance.base-url}")
    private String baseUrl;

    @Value("${ssafy.finance.api-key}")
    private String apiKey;

    @Transactional
    @Override
    public ExternalFinanceUser createUserKey(Long userRealId) {
        // 1) 외부용 랜덤 이메일 생성
        final String externalUserId = makeRandomAndUniqueUserId();

        // 2) 요청 바디와 헤더 구성
        Map<String, String> requestBody = Map.of(
                "apiKey", apiKey,
                "userId", externalUserId
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        // 3) 외부 API 호출
        String url = baseUrl + USER_KEY_END_POINT;
        Map<String, Object> respBody;
        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(url, entity, Map.class);
            respBody = resp.getBody();
        } catch (Exception e) {
            log.error("[ExternalFinanceUser] 외부 API 호출 실패");
            throw new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND);
        }

        if (respBody == null || respBody.get("userKey") == null) {
            throw new HeybobException(ExceptionStatus.EMPTY_USER_KEY);
        }

        // 4) userKey 추출 및 DB 저장
        String userKey = String.valueOf(respBody.get("userKey"));

        ExternalFinanceUser created = ExternalFinanceUser.builder()
                .userRealId(userRealId)
                .userId(externalUserId)
                .userKey(userKey)
                .build();

        externalFinanceUserRepository.save(created);
        log.info("외부 금융 사용자 생성 완료: userId={}, userKey={}", externalUserId, userKey);

        return created;
    }

    /**
     * 랜덤 이메일 생성 + 중복 체크
     */
    private String makeRandomAndUniqueUserId() {
        for (int i = 0; i < MAX_RETRY; i++) {
            String local = buildLocalPart();
            String candidate = local + EMAIL_DOMAIN;

            if (candidate.length() > MAX_EMAIL_LEN) {
                candidate = candidate.substring(0, MAX_EMAIL_LEN);
            }

            if (!externalFinanceUserRepository.existsByUserId(candidate)) {
                return candidate;
            }
            log.warn("중복 userId 감지: {} (retry {})", candidate, i + 1);
        }
        throw new IllegalStateException("고유한 external userId 생성 실패 (재시도 초과)");
    }

    /**
     * 시간+UUID조각+난수 기반 로컬 파트 생성
     */
    private String buildLocalPart() {
        String ts36 = Long.toString(Instant.now().toEpochMilli(), 36);
        String uuidPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        byte[] rnd = new byte[5];
        rng.nextBytes(rnd);
        String rand = Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);

        String raw = (ts36 + uuidPart + rand).toLowerCase(Locale.ROOT);
        String alnum = raw.replaceAll("[^a-z0-9]", "");

        if (alnum.length() > MAX_LOCAL_LEN) {
            alnum = alnum.substring(0, MAX_LOCAL_LEN);
        }
        return alnum;
    }
}