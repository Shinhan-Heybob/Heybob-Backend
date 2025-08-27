package com.shinhan.heybob.domain.settlement.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.common.util.KSTUtil;
import com.shinhan.heybob.domain.financePersonal.dto.FinanceHeader;
import com.shinhan.heybob.domain.financePersonal.dto.InquireDemandDepositAccountBalanceRequest;
import com.shinhan.heybob.domain.financePersonal.dto.PersonalAccountBalanceResponseDto;
import com.shinhan.heybob.domain.financePersonal.repository.ExternalFinanceUserRepository;
import com.shinhan.heybob.domain.financePersonal.util.UserAccountUtil;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.meal.service.ChatIntegrationService;
import com.shinhan.heybob.domain.notification.dto.ChatEventMessageDto;
import com.shinhan.heybob.domain.notification.model.NotificationEventType;
import com.shinhan.heybob.domain.notification.publisher.RedisStreamPublisher;
import com.shinhan.heybob.domain.settlement.dto.SettlementResponseDto;
import com.shinhan.heybob.domain.settlement.dto.UpdateDemandDepositAccountTransferRequest;
import com.shinhan.heybob.domain.settlement.entity.Settlement;
import com.shinhan.heybob.domain.settlement.entity.SettlementParticipant;
import com.shinhan.heybob.domain.settlement.model.SettlementStatus;
import com.shinhan.heybob.domain.settlement.model.TransferStatus;
import com.shinhan.heybob.domain.settlement.repository.SettlementParticipantRepository;
import com.shinhan.heybob.domain.settlement.repository.SettlementRepository;
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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final RedisStreamPublisher redisStreamPublisher;
    private final MealAppointmentRepository mealAppointmentRepository;
    private final UserAccountUtil userAccountUtil;
    private final ExternalFinanceUserRepository externalFinanceUserRepository;
    private final RestTemplate restTemplate;
    private final ChatIntegrationService chatIntegrationService;

    @Value("${ssafy.finance.base-url}")
    private String baseurl;

    @Value("${ssafy.finance.api-key}")
    private String apiKey;

    @Value("${ssafy.finance.account-type-unique-no}")
    private String accountTypeUniqueNo;

    @Transactional
    @Override
    public SettlementResponseDto createSettlement(
            Long userId, List<Long> participantsUserIds, int totalAmount, Long chatRoomId
    ) {
        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

        // 2) 참가자 id 중복 제거
        List<Long> distinctIds = participantsUserIds.stream().distinct().toList();

        // 3) 한 번에 유저 조회 및 존재 검증
        Map<Long, User> userMap = userRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<Long> missing = distinctIds.stream()
                .filter(id -> !userMap.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        int participantsCount = distinctIds.size();
        int perHead = totalAmount / participantsCount; // 정책: 나머지 버림

        MealAppointment mealAppointment = mealAppointmentRepository.findByChatRoomId(chatRoomId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));

        // 4) Settlement 생성 (상태: CREATED)
        Settlement settlement = Settlement.builder()
                .mealAppointment(mealAppointment)
                .initiator(initiator)
                .totalAmount(totalAmount)
                .perHeadAmount(perHead)
                .participantsCount(participantsCount)
                .status(SettlementStatus.CREATED)
                .build();

        Settlement saved = settlementRepository.save(settlement);

        // 5) 참가자 행 생성 (User 엔티티 주입)
        List<SettlementParticipant> rows = distinctIds.stream()
                .map(pid -> SettlementParticipant.builder()
                        .settlement(saved)
                        .participantUser(userMap.get(pid))
                        .amount(perHead)
                        .transferStatus(TransferStatus.PENDING)
                        .build())
                .toList();

        participantRepository.saveAll(rows);

        SettlementResponseDto responseDto =
                new SettlementResponseDto(
                        settlement.getId(),
                        initiator.getId(),
                        initiator.getName(),
                        perHead,
                        totalAmount,
                        participantsCount,
                        true,
                        false,
                        null
                );

        log.info("Settlement created successfully");
        return responseDto;
    }

    @Transactional
    @Override
    public void updateSettlement(Long userId, List<Long> participantsUserIds, int totalAmount, Long chatRoomId) {
        MealAppointment meal = mealAppointmentRepository.findByChatRoomId(chatRoomId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));

        Settlement settlement = settlementRepository.findByMealAppointment(meal)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SETTLEMENT_NOT_FOUND));

        if (settlement.getStatus() != SettlementStatus.CREATED) {
            throw new HeybobException(ExceptionStatus.SETTLEMENT_STATUS_BAD_REQUEST);
        }

        if (participantsUserIds == null || participantsUserIds.isEmpty()) {
            throw new HeybobException(ExceptionStatus.SETTLEMENT_PARTICIPANT_BAD_REQUEST);
        }

        List<Long> distinctIds = participantsUserIds.stream().distinct().toList();

        Map<Long, User> userMap = userRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<Long> missing = distinctIds.stream()
                .filter(id -> !userMap.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        int participantsCount = distinctIds.size();
        int perHead = totalAmount / participantsCount;

        // 1) 기존 참가자 행을 '먼저' 삭제하고 DB에 즉시 반영
        participantRepository.deleteBySettlementId(settlement.getId()); // flush 자동

        // 2) 본문 값 갱신
        settlement.setTotalAmount(totalAmount);
        settlement.setPerHeadAmount(perHead);
        settlement.setParticipantsCount(distinctIds.size());

        // 새 참가자 행 구성
        List<SettlementParticipant> newRows = distinctIds.stream()
                .map(pid -> SettlementParticipant.builder()
                        .settlement(settlement)
                        .participantUser(userMap.get(pid))
                        .amount(perHead)
                        .transferStatus(TransferStatus.PENDING)
                        .build())
                .toList();

        participantRepository.saveAll(newRows);
    }

    @Transactional
    @Override
    public void notifySettlement(Long chatRoomId, Long requesterId) {
        MealAppointment meal = mealAppointmentRepository.findByChatRoomId(chatRoomId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));

        Settlement settlement = settlementRepository.findByMealAppointment(meal)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SETTLEMENT_NOT_FOUND));

        if (settlement.getStatus() != SettlementStatus.CREATED) {
            throw new HeybobException(ExceptionStatus.SETTLEMENT_STATUS_BAD_REQUEST);
        }
        if (!settlement.getInitiator().getId().equals(requesterId)) {
            throw new HeybobException(ExceptionStatus.SETTLEMENT_INITIATOR_FORBIDDEN);
        }
        if (settlement.getMealAppointment().getChatRoomId() == null) {
            throw new HeybobException(ExceptionStatus.NOT_FOUND_CHAT_ROOM_ID);
        }

        // 상태 전환
        settlement.markInProgress();

        // 문구/라벨 생성
        String initiatorName = settlement.getInitiator().getName();
        String title = initiatorName + "님이 정산하기를 요청했습니다!";
        String perHeadLabel = NumberFormat.getInstance(Locale.KOREA).format(settlement.getPerHeadAmount()) + "원";
        String ctaLabel = perHeadLabel + " 송금하기";

        // 이벤트 구성 및 발행
        ChatEventMessageDto event = new ChatEventMessageDto(
                NotificationEventType.REQUESTED,
                settlement.getMealAppointment().getChatRoomId(),
                settlement.getId(),
                settlement.getInitiator().getId(),
                initiatorName,
                title,
                ctaLabel
        );

        redisStreamPublisher.publish(event);

        log.info("Settlement started and event published: settlementId={}, chatRoomId={}",
                settlement.getId(), settlement.getMealAppointment().getChatRoomId());
    }

    @Transactional
    @Override
    public SettlementResponseDto getSettlementInfo(Long userId, Long chatRoomId) {
        MealAppointment meal = mealAppointmentRepository.findByChatRoomId(chatRoomId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));

        Settlement settlement = settlementRepository.findByMealAppointment(meal)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SETTLEMENT_NOT_FOUND));

        if (settlement.getMealAppointment().getChatRoomId() == null) {
            throw new HeybobException(ExceptionStatus.NOT_FOUND_CHAT_ROOM_ID);
        }

        boolean isInitiator = settlement.getInitiator().getId().equals(userId);

        var mySpOpt = participantRepository
                .findBySettlement_IdAndParticipantUser_Id(settlement.getId(), userId);

        boolean isParticipant = mySpOpt.isPresent();
        Boolean myPaid = isParticipant
                ? (mySpOpt.get().getTransferStatus() == TransferStatus.SUCCESS)
                : null;

        return new SettlementResponseDto(
                settlement.getId(),
                settlement.getInitiator().getId(),
                settlement.getInitiator().getName(),
                settlement.getPerHeadAmount(),
                settlement.getTotalAmount(),
                settlement.getParticipantsCount(),
                isInitiator,
                isParticipant,
                myPaid
        );
    }

    @Transactional
    @Override
    public void paySettlement(Long userId, Long chatRoomId) {
        MealAppointment meal = mealAppointmentRepository.findByChatRoomId(chatRoomId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));

        Settlement settlement = settlementRepository.findByMealAppointment(meal)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SETTLEMENT_NOT_FOUND));

        if (settlement.getMealAppointment().getChatRoomId() == null) {
            throw new HeybobException(ExceptionStatus.NOT_FOUND_CHAT_ROOM_ID);
        }

        User withdrawalUser = userRepository.findById(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

        // 입금 계좌번호 조회 = 정산 개시자의 계좌번호
        User initiator = settlement.getInitiator();
        String depositAccountNo = userAccountUtil.getPersonalAccountNoByUserRealId(initiator.getId());
        // 출금 계좌번호 = 현재 사용자의 계좌번호
        String withdrawalAccountNo = userAccountUtil.getPersonalAccountNoByUserRealId(userId);

        // 거래금액 = 1인당 정산 금액
        String transactionBalance = String.valueOf(settlement.getPerHeadAmount());

        // 거래요약내용: 입금계좌표시
        String depositTransactionSummary = "1/N 정산하기 " + withdrawalUser.getName();
        // 거래요약내용: 출금계좌
        String withdrawalTransactionSummary = "1/N 정산하기 " + initiator.getName();

        String userKey = externalFinanceUserRepository.findUserKeyByUserRealId(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.EMPTY_USER_KEY));

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

        UpdateDemandDepositAccountTransferRequest request
                = new UpdateDemandDepositAccountTransferRequest(
                        header,
                depositAccountNo,
                depositTransactionSummary,
                transactionBalance,
                withdrawalAccountNo,
                withdrawalTransactionSummary
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UpdateDemandDepositAccountTransferRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseurl + "/edu/demandDeposit/updateDemandDepositAccountTransfer",
                entity,
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new HeybobException(ExceptionStatus.FINANCE_API_NOT_FOUND);
        }

        SettlementParticipant settlementParticipant =
                participantRepository.findBySettlement_IdAndParticipantUser_Id(settlement.getId(), userId)
                                .orElseThrow(() -> new HeybobException(ExceptionStatus.SETTLEMENT_PARTICIPANT_BAD_REQUEST));

        settlementParticipant.markSuccess();
        participantRepository.save(settlementParticipant);

        boolean allPaid = settlement.getParticipants().stream()
                .allMatch(SettlementParticipant::isSuccess);

        if (allPaid) {
            settlement.markCompleted();
            settlementRepository.save(settlement);
            log.info("정산 완료 처리됨: settlementId={}", settlement.getId());
        }

        log.info("계좌 이체 API - 정산 완료");
    }

}
