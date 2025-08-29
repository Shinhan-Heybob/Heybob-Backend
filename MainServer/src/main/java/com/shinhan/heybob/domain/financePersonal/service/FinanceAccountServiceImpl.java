package com.shinhan.heybob.domain.financePersonal.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.common.util.KSTUtil;
import com.shinhan.heybob.domain.financePersonal.dto.*;
import com.shinhan.heybob.domain.financePersonal.entity.PersonalAccount;
import com.shinhan.heybob.domain.financePersonal.repository.ExternalFinanceUserRepository;
import com.shinhan.heybob.domain.financePersonal.repository.PersonalAccountRepository;
import com.shinhan.heybob.domain.financePersonal.util.UserAccountUtil;
import com.shinhan.heybob.domain.user.entity.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
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

        String apiUrl = baseurl + "/edu/demandDeposit/inquireTransactionHistoryList";
        log.info("금융 API 호출 시작 - URL: {}, AccountNo: {}, Period: {} ~ {}", 
                apiUrl, accountNo, startDate, endDate);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<InquireTransactionHistoryListRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(apiUrl, entity, Map.class);
            log.info("금융 API 응답 - Status: {}", response.getStatusCode());
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("금융 API 호출 실패 - Status: {}, Body: {}", 
                        response.getStatusCode(), response.getBody());
                throw new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND);
            }
            
            if (response.getBody() == null) {
                log.error("금융 API 응답 본문이 null입니다");
                throw new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND);
            }
            
        } catch (RestClientException e) {
            log.error("금융 API 호출 중 RestClient 예외 발생 - URL: {}, Message: {}", 
                    apiUrl, e.getMessage(), e);
            throw new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND);
        } catch (HeybobException e) {
            throw e;  // HeybobException은 그대로 전파
        } catch (Exception e) {
            log.error("금융 API 호출 중 예기치 않은 예외 발생", e);
            throw new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND);
        }

        Map<String, Object> body = response.getBody();
        log.info("=== 금융 API 응답 본문 ===: {}", body);
        
        Map<String, Object> rec = (Map<String, Object>) body.get("REC");
        if (rec == null) {
            log.warn("금융 API 응답에 REC가 없습니다");
            return TransactionHistoryListResponseDto.builder()
                    .totalCount(0)
                    .transactionHistoryDtoList(new java.util.ArrayList<>())
                    .build();
        }
        
        log.info("=== REC 내용 ===: {}", rec);
        
        // totalCount가 null이거나 빈 문자열인 경우 처리
        String totalCountStr = (String) rec.get("totalCount");
        log.info("=== totalCountStr ===: '{}'", totalCountStr);
        
        int totalCount = (totalCountStr != null && !totalCountStr.isEmpty()) 
            ? Integer.parseInt(totalCountStr) 
            : 0;
        
        log.info("=== totalCount 파싱 결과 ===: {}", totalCount);
        
        // 거래 내역이 없는 경우 빈 리스트 반환
        if (totalCount == 0) {
            log.info("=== 거래 내역이 없습니다. 빈 리스트 반환 ===");
            return TransactionHistoryListResponseDto.builder()
                    .totalCount(0)
                    .transactionHistoryDtoList(new java.util.ArrayList<>())
                    .build();
        }
        
        log.info("=== 거래 내역 처리 계속 진행 ===");
        
        List<Map<String, Object>> list = (List<Map<String, Object>>) rec.get("list");
        
        // list가 null이거나 비어있는 경우 빈 리스트로 처리
        if (list == null || list.isEmpty()) {
            log.info("거래 내역 리스트가 비어있습니다");
            return TransactionHistoryListResponseDto.builder()
                    .totalCount(0)
                    .transactionHistoryDtoList(new java.util.ArrayList<>())
                    .build();
        }

        List<TransactionHistoryDto> dtoList = list.stream()
                .map(item -> {
                    // 각 거래별로 상대방 계좌 정보 확인
                    String transactorAccountNo = (String) item.get("transactionAccountNo");
                    String transactorName = null;
                    
                    if (transactorAccountNo != null && !transactorAccountNo.isEmpty()) {
                        try {
                            transactorName = userAccountUtil.getUserNameByPersonalAccountNo(transactorAccountNo);
                        } catch (Exception e) {
                            // 상대방이 우리 시스템 사용자가 아닐 수 있음
                            log.debug("거래 상대방을 찾을 수 없음: {}", transactorAccountNo);
                            transactorName = (String) item.get("transactionSummary"); // 거래 요약 정보 사용
                            if (transactorName == null || transactorName.isEmpty()) {
                                transactorName = "외부 계좌";
                            }
                        }
                    } else {
                        // transactionAccountNo가 없는 경우
                        transactorName = (String) item.get("transactionSummary");
                        if (transactorName == null || transactorName.isEmpty()) {
                            transactorName = "알 수 없음";
                        }
                    }
                    
                    return TransactionHistoryDto.builder()
                        .transactionUniqueNo((String) item.get("transactionUniqueNo"))
                        .transactionDate((String) item.get("transactionDate"))
                        .transactionTime((String) item.get("transactionTime"))
                        .transactionTypeName((String) item.get("transactionTypeName"))
                        .transactionBalance((String) item.get("transactionBalance"))
                        .transactionAfterBalance((String) item.get("transactionAfterBalance"))
                        .transactorName(transactorName)
                        .eventTitle((String) item.get("transactionMemo"))
                        .build();
                })
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
