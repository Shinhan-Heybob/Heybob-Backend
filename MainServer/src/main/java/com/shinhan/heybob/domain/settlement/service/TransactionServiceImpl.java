package com.shinhan.heybob.domain.settlement.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.settlement.dto.CreateSettlementRequestDto;
import com.shinhan.heybob.domain.settlement.entity.Settlement;
import com.shinhan.heybob.domain.settlement.entity.SettlementParticipant;
import com.shinhan.heybob.domain.settlement.model.TransferStatus;
import com.shinhan.heybob.domain.settlement.repository.SettlementParticipantRepository;
import com.shinhan.heybob.domain.settlement.repository.SettlementRepository;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService{

    private final SettlementRepository settlementRepository;
    private final SettlementParticipantRepository participantRepository;
    private final MealAppointmentRepository mealAppointmentRepository;
    private final UserRepository userRepository;

    @Transactional
    @Override
    public void createSettlement(Long userId, CreateSettlementRequestDto requestDto) {
        if (requestDto.participantsUserIds() == null || requestDto.participantsUserIds().isEmpty()) {
            throw new HeybobException(ExceptionStatus.EMPTY_PARTICIPANTS_USER_IDS);
        }

        if (requestDto.totalAmount() <= 0) {
            throw new HeybobException(ExceptionStatus.INVALID_TOTAL_AMOUNT);
        }

        int participantsCount = requestDto.participantsUserIds().size();
        int perHead = requestDto.totalAmount() / participantsCount; // 정책: 나머지 버림

        MealAppointment mealAppointment = mealAppointmentRepository.findById(requestDto.mealAppointmentId())
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));

        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

        Settlement settlement = Settlement.builder()
                .mealAppointment(mealAppointment)
                .initiator(initiator)               // 호출자 = 정산 시작자
                .totalAmount(requestDto.totalAmount())
                .perHeadAmount(perHead)
                .participantsCount(participantsCount)
                .build();

        Settlement saved = settlementRepository.save(settlement);

        // 3) 참가자 행 생성
        List<SettlementParticipant> rows = requestDto.participantsUserIds()
                .stream()
                .distinct() // 혹시 중복 제거
                .map(pid -> SettlementParticipant.builder()
                        .settlement(saved)
                        .participantUserId(pid)
                        .amount(perHead)
                        .transferStatus(TransferStatus.PENDING)
                        .build())
                .toList();

        participantRepository.saveAll(rows);

        // 4) (선택) Redis Stream 알림 발행은 여기서 처리
        // redisStreamPublisher.publishSettlementCreated(saved.getId(), perHead, requestDto.participantsUserIds());
    }
}
