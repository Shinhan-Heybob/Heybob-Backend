package com.shinhan.heybob.domain.finance.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.common.util.KSTUtil;
import com.shinhan.heybob.domain.finance.dto.CreateDemandDepositAccountRequest;
import com.shinhan.heybob.domain.finance.dto.FinanceHeader;
import com.shinhan.heybob.domain.finance.entity.PersonalAccount;
import com.shinhan.heybob.domain.finance.repository.PersonalAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class FinanceAccountServiceImpl implements FinanceAccountService{

    private final RestTemplate restTemplate = new RestTemplate();
    private final PersonalAccountRepository personalAccountRepository;

    @Value("${ssafy.finance.base-url}")
    private String baseurl;

    @Value("${ssafy.finance.api-key}")
    private String apiKey;

    @Value("${ssafy.finance.account-type-unique-no}")
    private String accountTypeUniqueNo;

    @Override
    public void createDemandDepositAccount(Long externalFinanceUserId, String userKey) {
        // Header 생성
        FinanceHeader header = new FinanceHeader(
                "createDemandDepositAccount",
                KSTUtil.nowDateKst(),
                KSTUtil.nowTimeKst(),
                "00100",
                "001",
                "createDemandDepositAccount",
                KSTUtil.makeUniqueNo(),
                apiKey,
                userKey
        );

        // Body
        CreateDemandDepositAccountRequest request = new CreateDemandDepositAccountRequest(header, accountTypeUniqueNo);

        // json 전송
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateDemandDepositAccountRequest> entity = new HttpEntity<>(request,  headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseurl + "/edu/demandDeposit/createDemandDepositAccount",
                entity,
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND);
        }

        // 개인계좌 엔티티 생성
        createPersonalAccount(externalFinanceUserId, (String) response.getBody().get("accountNo"));

    }

    @Override
    public void createPersonalAccount(Long externalFinanceUserId, String accountNo) {
        PersonalAccount personalAccount = new PersonalAccount().builder()
                .externalFinanceUserId(externalFinanceUserId)
                .accountNo(accountNo)
                .build();

        personalAccountRepository.save(personalAccount);

    }
}
