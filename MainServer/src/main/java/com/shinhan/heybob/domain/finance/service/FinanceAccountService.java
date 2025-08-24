package com.shinhan.heybob.domain.finance.service;

public interface FinanceAccountService {

    // 사용자별 수시입출금 계좌 생성
    void createDemandDepositAccount(Long externalFinanceUserId, String userKey);

    void createPersonalAccount(Long externalFinanceUserId, String accountNo);
}
