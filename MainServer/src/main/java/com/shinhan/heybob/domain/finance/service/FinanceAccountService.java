package com.shinhan.heybob.domain.finance.service;

import org.springframework.boot.autoconfigure.mail.MailProperties;

import java.util.Map;

public interface FinanceAccountService {

    // 사용자별 수시입출금 계좌 생성
    Map<String, Object> createDemandDepositAccount(String userKey);
}
