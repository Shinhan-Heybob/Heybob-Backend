package com.shinhan.heybob.domain.financePersonal.service;

import com.shinhan.heybob.domain.financePersonal.dto.TransactionHistoryListResponseDto;
import com.shinhan.heybob.domain.financePersonal.dto.PersonalAccountBalanceResponseDto;
import com.shinhan.heybob.domain.financePersonal.dto.PersonalAccountNoResponseDto;

public interface FinanceAccountService {

    // 사용자별 수시입출금 계좌 생성
    void createDemandDepositAccount(Long externalFinanceUserId, String userKey);

    void createPersonalAccount(Long externalFinanceUserId, String accountNo);

    PersonalAccountNoResponseDto getPersonalAccountNo(Long userId);

    PersonalAccountBalanceResponseDto getPersonalAccountBalance(Long userId);

    TransactionHistoryListResponseDto getTransactionHistoryList(Long userId, String startDate, String endDate);

    void deposit(Long userId, int amount);
}
