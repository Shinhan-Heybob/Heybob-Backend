package com.shinhan.heybob.domain.settlement.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
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

import java.util.ArrayList;
import java.util.List;
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

    @Transactional
    @Override
    public void createSettlement(
            Long userId, List<Long> participantsUserIds, int totalAmount, MealAppointment mealAppointment
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
    public void updateSettlement(Settlement settlement, Long userId, List<Long> participantsUserIds, int totalAmount) {
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
}
