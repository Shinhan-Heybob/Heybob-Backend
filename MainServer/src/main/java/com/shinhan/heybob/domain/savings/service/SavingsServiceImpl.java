package com.shinhan.heybob.domain.savings.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.common.util.KSTUtil;
import com.shinhan.heybob.domain.financePersonal.dto.FinanceHeader;
import com.shinhan.heybob.domain.financePersonal.repository.ExternalFinanceUserRepository;
import com.shinhan.heybob.domain.financePersonal.repository.PersonalAccountRepository;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.meal.repository.MealParticipantRepository;
import com.shinhan.heybob.domain.notification.service.ChatBroadcastSenderImpl;
import com.shinhan.heybob.domain.savings.dto.CreateAccountRequest;
import com.shinhan.heybob.domain.savings.entity.SavingsAccount;
import com.shinhan.heybob.domain.savings.entity.SavingsDeposit;
import com.shinhan.heybob.domain.savings.entity.SavingsPlan;
import com.shinhan.heybob.domain.savings.repository.SavingsAccountRepository;
import com.shinhan.heybob.domain.savings.repository.SavingsDepositRepository;
import com.shinhan.heybob.domain.savings.repository.SavingsPlanRepository;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final SavingsPlanRepository savingsPlanRepository;
    private final MealParticipantRepository mealParticipantRepository;
    private final SavingsDepositRepository savingsDepositRepository;
    private final ChatBroadcastSenderImpl chatBroadcastSenderImpl;

    @Value("${ssafy.finance.base-url}")
    private String baseUrl;

    @Value("${ssafy.finance.api-key}")
    private String apiKey;

    @Value("${ssafy.finance.savings-account-type-unique-no}")
    private String accountTypeUniqueNo;

    @Transactional
    @Override
    public void createSavingsAccount(Long userId, Long mealId, int perAmount, int totalAmount) {
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
                baseUrl + "/edu/savings/createAccount",
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

        int cycles = totalAmount / perAmount; // 회수 계산 (나머지는 정책에 따라 처리)
        if (cycles <= 0) throw new HeybobException(ExceptionStatus.BAD_REQUEST_SAVINGS_CYCLE);

        var saved = savingsAccountRepository.save(
                SavingsAccount.builder()
                        .accountNo(accountNo)
                        .mealAppointment(mealAppointment)
                        .ownerUser(creator)
                        .build()
        );

// 다음 알림 시각: 오늘 요일 기준, 예: 오전 9시 KST
        var zone = java.time.ZoneId.of("Asia/Seoul");
        var nowKst = java.time.ZonedDateTime.now(zone).toLocalDateTime();
        var notifyTime = java.time.LocalTime.of(9, 0); // 정책에 맞게
        var startDate = nowKst.toLocalDate();

// 오늘 알림 시간이 이미 지났으면 다음 주
        var firstNotifyAt = java.time.LocalDateTime.of(startDate, notifyTime);
        if (!firstNotifyAt.isAfter(nowKst)) firstNotifyAt = firstNotifyAt.plusWeeks(1);

        var plan = com.shinhan.heybob.domain.savings.entity.SavingsPlan.builder()
                .savingsAccount(saved)
                .perHeadBalance(perAmount)
                .totalCycles(cycles)
                .sentCycles(0)
                .notifyDayOfWeek(firstNotifyAt.getDayOfWeek().getValue())
                .notifyTime(notifyTime)
                .nextNotifyAt(firstNotifyAt)
                .status(com.shinhan.heybob.domain.savings.entity.SavingsPlan.PlanStatus.ACTIVE)
                .build();

        savingsPlanRepository.save(plan);

        // ✅ 적금 요청 브로드캐스트 (방이 있는 경우에만)
        Long chatRoomId = mealAppointment.getChatRoomId();
        if (chatRoomId != null) {
            String content = String.format("%s님이 적금을 요청했습니다. 1/N 금액: %,d원",
                    creator.getName(), perAmount);

            // afterCommit으로 안전하게 전송
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            chatBroadcastSenderImpl.sendSavingsRequest(
                                    String.valueOf(chatRoomId),                 // roomId
                                    String.valueOf(creator.getId()),            // senderId
                                    creator.getStudentId(),                     // studentId
                                    creator.getName(),                          // senderName
                                    creator.getProfileUrl(),                    // profileImageUrl
                                    content,                                    // 상단 content (채팅에 보일 문장)
                                    String.valueOf(saved.getId()),               // settlementId(=saving 식별자)
                                    String.valueOf(creator.getId()),            // requesterId
                                    creator.getName(),                          // requesterName
                                    creator.getStudentId(),                     // requesterStudentId
                                    creator.getProfileUrl(),                    // requesterProfileImg
                                    perAmount,                                   // requestAmount (이번 회차 1/N 금액)
                                    "http://localhost:8080/savings/" + chatRoomId + "/pay"
                            );
                        }
                    }
            );
        }
    }

    @Transactional
    @Override
    public void paySavingsAccount(Long userId, Long chatRoomId) {
        String username = userRepository.findById(userId).get().getName();

        MealAppointment mealAppointment = mealAppointmentRepository.findByChatRoomId(chatRoomId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));
        Long mealId = mealAppointment.getId();
        String mealName = mealAppointment.getName();

        // 사용자가 밥약 참여자인지 검증
        if (!mealParticipantRepository.existsByMealAppointment_IdAndUser_Id(mealId, userId)) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        // 1) 계좌/플랜 조회
        SavingsAccount account = savingsAccountRepository.findByMealAppointment_Id(mealId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SAVINGS_ACCOUNT_NOT_FOUND));

        SavingsPlan plan = savingsPlanRepository.findBySavingsAccount_Id(account.getId())
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SAVINGS_PLAN_NOT_FOUND));

        if (plan.getStatus() == SavingsPlan.PlanStatus.COMPLETED) {
            throw new HeybobException(ExceptionStatus.SAVINGS_PLAN_COMPLETED);
        }

        // "현재 회차" 계산: 보낸 알림 수 + 1 (알림 전 선납 허용 정책이면 그대로 사용)
        int currentCycle = Math.min(plan.getSentCycles() + 1, plan.getTotalCycles());

        // 2) 이 회차에 이미 성공 납입했는지
        boolean alreadyPaid = savingsDepositRepository
                .existsBySavingsAccount_IdAndParticipantUser_IdAndCycleNoAndStatus(
                        account.getId(), userId, currentCycle, SavingsDeposit.TransferStatus.SUCCESS);
        if (alreadyPaid) {
            return; // 멱등 처리: 조용히 성공 반환
        }

        // 3) 외부 이체 준비
        String userKey = externalFinanceUserRepository.findUserKeyByUserRealId(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.EMPTY_USER_KEY));

        Long extUserId = externalFinanceUserRepository.findIdByUserRealId(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND));

        String fromAccountNo = personalAccountRepository.findAccountNoByExternalFinanceUserId(extUserId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND));

        String toAccountNo = account.getAccountNo();
        int amount = plan.getPerHeadBalance();
        String idemKey = java.util.UUID.randomUUID().toString();

        // 4) 원장 레코드(PENDING) 선기록 (멱등/감사 목적)
        SavingsDeposit deposit = savingsDepositRepository.findBySavingsAccount_IdAndParticipantUser_IdAndCycleNo(
                account.getId(), userId, currentCycle
        ).orElseGet(() -> SavingsDeposit.builder()
                .savingsAccount(account)
                .participantUser(com.shinhan.heybob.domain.user.entity.User.builder().id(userId).build())
                .cycleNo(currentCycle)
                .amount(amount)
                .idempotencyKey(idemKey)
                .build());

        savingsDepositRepository.save(deposit);

        // 5) 외부 이체 호출 (샘플; 실제 스펙에 맞게 DTO/URL 교체)
        FinanceHeader header = new FinanceHeader(
                "updateDemandDepositAccountTransfer",
                KSTUtil.nowDateKst(),
                KSTUtil.nowTimeKst(),
                "00100",
                "001",
                "updateDemandDepositAccountTransfer",
                KSTUtil.makeUniqueNo(),
                apiKey,
                userKey
        );

        UpdateDemandDepositAccountTransferRequest request = new UpdateDemandDepositAccountTransferRequest(
                header,
                toAccountNo,
                "1/N 모으기 " + username,
                String.valueOf(amount),
                fromAccountNo,
                "1/N 모으기 " + mealName
        );

        var entity = new org.springframework.http.HttpEntity<>(request, jsonHeaders());
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl + "/edu/demandDeposit/updateDemandDepositAccountTransfer", entity, Map.class
        );

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            deposit.markFailed();
            savingsDepositRepository.save(deposit);
            throw new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND);
        }

        Object txId = null;
        Map body = resp.getBody();
        if (body != null) {
            Object recObj = body.get("REC");
            if (recObj instanceof Map<?,?> rec) {
                txId = rec.get("transactionUniqueNo");
            }
        }
        String externalTxId = txId != null ? txId.toString() : "";;

        // 6) 성공 처리
        deposit.markSuccess(externalTxId);
        savingsDepositRepository.save(deposit);

        // ✅ 적금 납입 완료 브로드캐스트
        User payer = userRepository.findById(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

        if (chatRoomId != null) {
            String content = String.format("%s님이 %,d원을 적금했습니다.", payer.getName(), amount);

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override public void afterCommit() {
                            chatBroadcastSenderImpl.sendSavingsComplete(
                                    String.valueOf(chatRoomId),         // roomId
                                    String.valueOf(payer.getId()),      // senderId
                                    payer.getStudentId(),               // studentId
                                    payer.getName(),                    // senderName
                                    payer.getProfileUrl(),              // profileImageUrl
                                    content,                            // 상단 content
                                    String.valueOf(plan.getId()),       // settlementId(=saving 식별자)
                                    String.valueOf(account.getId()),    // recipientId (예: 적금계좌 id)
                                    "모임적금 1/N 모으기",                          // recipientName (컨슈머 예시와 동일)
                                    amount                              // completedAmount
                            );
                        }
                    }
            );
        }

    }

    private HttpHeaders jsonHeaders() {
        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

}
