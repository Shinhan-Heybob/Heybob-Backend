package com.shinhan.heybob.domain.financePersonal.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.common.util.KSTUtil;
import com.shinhan.heybob.domain.financePersonal.dto.*;
import com.shinhan.heybob.domain.financePersonal.entity.PersonalAccount;
import com.shinhan.heybob.domain.financePersonal.repository.ExternalFinanceUserRepository;
import com.shinhan.heybob.domain.financePersonal.repository.PersonalAccountRepository;
import com.shinhan.heybob.domain.financePersonal.util.UserAccountUtil;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FinanceAccountServiceImpl implements FinanceAccountService{

    private final RestTemplate restTemplate;
    private final PersonalAccountRepository personalAccountRepository;
    private final UserAccountUtil userAccountUtil;
    private final ExternalFinanceUserRepository externalFinanceUserRepository;

    @Value("${ssafy.finance.base-url}")
    private String baseurl;

    @Value("${ssafy.finance.api-key}")
    private String apiKey;

    @Value("${ssafy.finance.account-type-unique-no}")
    private String accountTypeUniqueNo;

    @Transactional
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
        CreateDemandDepositAccountRequest request =
                new CreateDemandDepositAccountRequest(header, accountTypeUniqueNo);

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
        Map<String, Object> body = response.getBody();
        Map<String, Object> rec = (Map<String, Object>) body.get("REC");
        String accountNo = (String) rec.get("accountNo");

        createPersonalAccount(externalFinanceUserId, accountNo);

    }

    @Transactional
    @Override
    public void createPersonalAccount(Long externalFinanceUserId, String accountNo) {
        PersonalAccount personalAccount = new PersonalAccount().builder()
                .externalFinanceUserId(externalFinanceUserId)
                .accountNo(accountNo)
                .build();

        personalAccountRepository.save(personalAccount);

    }

    @Override
    public PersonalAccountNoResponseDto getPersonalAccountNo(Long userId) {
        String accountNo = userAccountUtil.getPersonalAccountNoByUserRealId(userId);
        return new PersonalAccountNoResponseDto(accountNo);
    }

    // 계좌 잔액 조회
    @Override
    public PersonalAccountBalanceResponseDto getPersonalAccountBalance(Long userId) {
        String accountNo = userAccountUtil.getPersonalAccountNoByUserRealId(userId);
        String userKey = externalFinanceUserRepository.findUserKeyByUserRealId(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.EMPTY_USER_KEY));

        // Header
        FinanceHeader header = new FinanceHeader(
                "inquireDemandDepositAccountBalance",
                KSTUtil.nowDateKst(),
                KSTUtil.nowTimeKst(),
                "00100",
                "001",
                "inquireDemandDepositAccountBalance",
                KSTUtil.makeUniqueNo(),
                apiKey,
                userKey
        );

        // 요청
        InquireDemandDepositAccountBalanceRequest request =
                new InquireDemandDepositAccountBalanceRequest(header, accountNo);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<InquireDemandDepositAccountBalanceRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseurl + "/edu/demandDeposit/inquireDemandDepositAccountBalance",
                entity,
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND);
        }

        Map<String, Object> body = response.getBody();
        Map<String, Object> rec = (Map<String, Object>) body.get("REC");
        String balance = (String) rec.get("accountBalance");

        return new PersonalAccountBalanceResponseDto().builder()
                .balance(balance)
                .build();
    }

    @Override
    public TransactionHistoryListResponseDto getTransactionHistoryList(
            Long userId, String startDate, String endDate
    ) {
        String accountNo = userAccountUtil.getPersonalAccountNoByUserRealId(userId);
        String userKey = externalFinanceUserRepository.findUserKeyByUserRealId(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.EMPTY_USER_KEY));

        // Header
        FinanceHeader header = new FinanceHeader(
                "inquireTransactionHistoryList",
                KSTUtil.nowDateKst(),
                KSTUtil.nowTimeKst(),
                "00100",
                "001",
                "inquireTransactionHistoryList",
                KSTUtil.makeUniqueNo(),
                apiKey,
                userKey
        );

        InquireTransactionHistoryListRequest request = new InquireTransactionHistoryListRequest(
                header, accountNo, startDate, endDate, "A", "DESC"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<InquireTransactionHistoryListRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseurl + "/edu/demandDeposit/inquireTransactionHistoryList",
                entity,
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND);
        }

        Map<String, Object> body = response.getBody();
        Map<String, Object> rec = (Map<String, Object>) body.get("REC");
        int totalCount = Integer.parseInt((String) rec.get("totalCount"));
        String transactionAccountNo = (String) rec.get("transactionAccountNo");
        String transactorName = userAccountUtil.getUserNameByPersonalAccountNo(transactionAccountNo);
        List<Map<String, Object>> list = (List<Map<String, Object>>) rec.get("list");


        List<TransactionHistoryDto> dtoList = list.stream()
                .map(item -> TransactionHistoryDto.builder()
                        .transactionUniqueNo((String) item.get("transactionUniqueNo"))
                        .transactionDate((String) item.get("transactionDate"))
                        .transactionTime((String) item.get("transactionTime"))
                        .transactionTypeName((String) item.get("transactionTypeName"))
                        .transactionBalance((String) item.get("transactionBalance"))
                        .transactionAfterBalance((String) item.get("transactionAfterBalance"))
                        .transactorName(transactorName)
                        .eventTitle((String) item.get("transactionMemo"))
                        .build()
                )
                .toList();

        // 6. 최종 ResponseDto 생성
        return TransactionHistoryListResponseDto.builder()
                .totalCount(totalCount)
                .transactionHistoryDtoList(dtoList)
                .build();
    }

    @Override
    public void deposit(Long userId, int amount) {
        String userKey = externalFinanceUserRepository.findUserKeyByUserRealId(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.EMPTY_USER_KEY));
        String accountNo = userAccountUtil.getPersonalAccountNoByUserRealId(userId);

        FinanceHeader header = new FinanceHeader(
                "updateDemandDepositAccountDeposit",
                KSTUtil.nowDateKst(),
                KSTUtil.nowTimeKst(),
                "00100",
                "001",
                "updateDemandDepositAccountDeposit",
                KSTUtil.makeUniqueNo(),
                apiKey,
                userKey
        );

        UpdateDemandDepositAccountDepositRequest request = new UpdateDemandDepositAccountDepositRequest(
                header,
                accountNo,
                String.valueOf(amount),
                "입금"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UpdateDemandDepositAccountDepositRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseurl + "/edu/demandDeposit/updateDemandDepositAccountDeposit",
                entity,
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND);
        }

    }
}
