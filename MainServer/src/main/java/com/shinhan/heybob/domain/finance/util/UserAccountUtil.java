package com.shinhan.heybob.domain.finance.util;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.finance.repository.ExternalFinanceUserRepository;
import com.shinhan.heybob.domain.finance.repository.PersonalAccountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAccountUtil {

    private final ExternalFinanceUserRepository externalFinanceUserRepository;
    private final PersonalAccountRepository personalAccountRepository;

    @Transactional
    public String getPersonalAccountNoByUserRealId(Long userRealId) {
        Long externalFinanceUserId = externalFinanceUserRepository.findIdByUserRealId(userRealId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

        return personalAccountRepository.findAccountNoByExternalFinanceUserId(externalFinanceUserId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));
    }

}
