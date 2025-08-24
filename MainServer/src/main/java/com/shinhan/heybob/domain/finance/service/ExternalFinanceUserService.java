package com.shinhan.heybob.domain.finance.service;

import com.shinhan.heybob.domain.finance.entity.ExternalFinanceUser;

public interface ExternalFinanceUserService {

    ExternalFinanceUser createUserKey(Long userId);
}
