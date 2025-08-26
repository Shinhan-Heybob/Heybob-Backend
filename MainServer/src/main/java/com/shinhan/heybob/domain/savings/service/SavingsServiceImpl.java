package com.shinhan.heybob.domain.savings.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.common.util.KSTUtil;
import com.shinhan.heybob.domain.financePersonal.dto.FinanceHeader;
import com.shinhan.heybob.domain.financePersonal.dto.PersonalAccountBalanceResponseDto;
import com.shinhan.heybob.domain.financePersonal.repository.ExternalFinanceUserRepository;
import com.shinhan.heybob.domain.financePersonal.repository.PersonalAccountRepository;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.savings.controller.CreateAccountRequest;
import com.shinhan.heybob.domain.savings.entity.SavingsAccount;
import com.shinhan.heybob.domain.savings.repository.SavingsAccountRepository;
import com.shinhan.heybob.domain.settlement.dto.UpdateDemandDepositAccountTransferRequest;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SavingsServiceImpl implements SavingsService {

    private final SavingsAccountRepository savingsAccountRepository;
    private final ExternalFinanceUserRepository externalFinanceUserRepository;
    private final PersonalAccountRepository personalAccountRepository;
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final MealAppointmentRepository mealAppointmentRepository;

    @Value("${ssafy.finance.base-url}")
    private String baseurl;

    @Value("${ssafy.finance.api-key}")
    private String apiKey;

    @Value("${ssafy.finance.savings-account-type-unique-no}")
    private String accountTypeUniqueNo;

    @Transactional
    @Override
    public void createSavingsAccount(Long userId, Long mealId) {
        String userKey = externalFinanceUserRepository.findUserKeyByUserRealId(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.EMPTY_USER_KEY));

        Long externalFinanceUserId = externalFinanceUserRepository.findIdByUserRealId(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND));

        String withdrawalAccountNo =
                personalAccountRepository.findAccountNoByExternalFinanceUserId(externalFinanceUserId)
                        .orElseThrow(() -> new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND));

        FinanceHeader header = new FinanceHeader(
                "createAccount",
                KSTUtil.nowDateKst(),
                KSTUtil.nowTimeKst(),
                "00100",
                "001",
                "createAccount",
                KSTUtil.makeUniqueNo(),
                apiKey,
                userKey
        );

        CreateAccountRequest request = new CreateAccountRequest(
                header,
                withdrawalAccountNo,
                accountTypeUniqueNo,
                "1"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateAccountRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseurl + "/edu/savings/createAccount",
                entity,
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND);
        }

        Map<String, Object> body = response.getBody();
        Map<String, Object> rec = (Map<String, Object>) body.get("REC");
        String accountNo = (String) rec.get("accountNo");

        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

        MealAppointment mealAppointment = mealAppointmentRepository.findById(mealId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));

        savingsAccountRepository.save(
                SavingsAccount.builder()
                        .accountNo(accountNo)
                        .mealAppointment(mealAppointment)
                        .ownerUser(creator)
                        .build()
        );
    }
}
