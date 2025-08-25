package com.shinhan.heybob.domain.settlement.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.notification.dto.ChatEventMessageDto;
import com.shinhan.heybob.domain.notification.model.NotificationEventType;
import com.shinhan.heybob.domain.notification.publisher.RedisStreamPublisher;
import com.shinhan.heybob.domain.settlement.dto.SettlementResponseDto;
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
import org.springframework.stereotype.Service;

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
public class TransactionServiceImpl implements TransactionService{

    private final SettlementRepository settlementRepository;
    private final SettlementParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final RedisStreamPublisher redisStreamPublisher;
    private final MealAppointmentRepository mealAppointmentRepository;

    @Transactional
    @Override
    public void createSettlement(
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

        log.info("Settlement created successfully");
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

        // 기존 참가자 제거 (orphanRemoval=true면 DELETE)
        List<SettlementParticipant> oldRows = new ArrayList<>(settlement.getParticipants());
        participantRepository.deleteAll(oldRows);

        // 새 참가자 행 구성
        List<SettlementParticipant> newRows = distinctIds.stream()
                .map(pid -> SettlementParticipant.builder()
                        .settlement(settlement)
                        .participantUser(userMap.get(pid))
                        .amount(perHead)
                        .transferStatus(TransferStatus.PENDING)
                        .build())
                .toList();

        // 정산 본문 값 갱신
        settlement.setTotalAmount(totalAmount);
        settlement.setPerHeadAmount(perHead);
        settlement.setParticipantsCount(participantsCount);

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

        // 퍼블리셔 주입 필요
        redisStreamPublisher.publish(event);

        log.info("Settlement started and event published: settlementId={}, chatRoomId={}",
                settlement.getId(), settlement.getMealAppointment().getChatRoomId());
    }

    @Transactional
    @Override
    public SettlementResponseDto getSettlementInfo(Long chatRoomId) {
        MealAppointment meal = mealAppointmentRepository.findByChatRoomId(chatRoomId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));

        Settlement settlement = settlementRepository.findByMealAppointment(meal)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SETTLEMENT_NOT_FOUND));

        if (settlement.getStatus() != SettlementStatus.CREATED) {
            throw new HeybobException(ExceptionStatus.SETTLEMENT_STATUS_BAD_REQUEST);
        }

        if (settlement.getMealAppointment().getChatRoomId() == null) {
            throw new HeybobException(ExceptionStatus.NOT_FOUND_CHAT_ROOM_ID);
        }

        return new  SettlementResponseDto(
                settlement.getTotalAmount(),
                settlement.getParticipantsCount(),
                settlement.getPerHeadAmount()
        );
    }

}
