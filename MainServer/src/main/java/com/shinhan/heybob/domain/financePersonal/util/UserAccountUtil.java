package com.shinhan.heybob.domain.financePersonal.util;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.financePersonal.repository.ExternalFinanceUserRepository;
import com.shinhan.heybob.domain.financePersonal.repository.PersonalAccountRepository;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAccountUtil {

    private final ExternalFinanceUserRepository externalFinanceUserRepository;
    private final PersonalAccountRepository personalAccountRepository;
    private final UserRepository userRepository;

    @Transactional
    public String getPersonalAccountNoByUserRealId(Long userRealId) {
        Long externalFinanceUserId = externalFinanceUserRepository.findIdByUserRealId(userRealId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

        return personalAccountRepository.findAccountNoByExternalFinanceUserId(externalFinanceUserId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));
    }

    @Transactional
    public String getUserNameByPersonalAccountNo(String personalAccountNo) {
        Long externalId = personalAccountRepository.findExternalFinanceUserIdByAccountNo(personalAccountNo)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.EXTERNAL_FINANCE_USER_ID_NOT_FOUND));

        Long userId = externalFinanceUserRepository.findUserRealIdById(externalId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

        String userName = userRepository.findNameById(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

        return userName;
    }

}
