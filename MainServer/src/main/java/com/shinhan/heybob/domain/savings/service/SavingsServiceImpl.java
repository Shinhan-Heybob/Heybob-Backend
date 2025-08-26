package com.shinhan.heybob.domain.savings.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.common.util.KSTUtil;
import com.shinhan.heybob.domain.financePersonal.dto.FinanceHeader;
import com.shinhan.heybob.domain.financePersonal.repository.ExternalFinanceUserRepository;
import com.shinhan.heybob.domain.financePersonal.repository.PersonalAccountRepository;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.savings.dto.CreateAccountRequest;
import com.shinhan.heybob.domain.savings.entity.SavingsAccount;
import com.shinhan.heybob.domain.savings.repository.SavingsAccountRepository;
import com.shinhan.heybob.domain.savings.repository.SavingsPlanRepository;
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
    private final SavingsPlanRepository savingsPlanRepository;

    @Value("${ssafy.finance.base-url}")
    private String baseurl;

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
    }

    private void registerAutoWithdrawalAccount() {}
}
