package com.shinhan.heybob.domain.settlement.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.settlement.dto.SettlementPageResponseDto;
import com.shinhan.heybob.domain.settlement.dto.SettlementParticipantItemDto;
import com.shinhan.heybob.domain.settlement.entity.Settlement;
import com.shinhan.heybob.domain.settlement.model.TransferStatus;
import com.shinhan.heybob.domain.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementQueryService {

    private final SettlementRepository settlementRepository;
    private final MealAppointmentRepository mealAppointmentRepository;

    // 채팅방 기준으로 조회하는 버전도 필요하면 제공
    @Transactional(readOnly = true)
    public SettlementPageResponseDto getSettlementPageByChatRoom(Long chatRoomId) {
        // 모임 정보는 항상 가져옴
        MealAppointment mealAppointment = mealAppointmentRepository.findByChatRoomId(chatRoomId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));
        
        // 정산 정보는 있으면 가져오고 없으면 null
        return settlementRepository.findDetailByChatRoomId(chatRoomId)
                .map(this::mapToSettlementPageResponseDto)
                .orElse(createEmptySettlementPageResponse(mealAppointment));
    }

    private SettlementPageResponseDto mapToSettlementPageResponseDto(Settlement s) {
        List<SettlementParticipantItemDto> items = s.getParticipants().stream()
                .map(sp -> new SettlementParticipantItemDto(
                        sp.getParticipantUser().getId(),
                        sp.getParticipantUser().getName(),
                        sp.getAmount(),
                        sp.getTransferStatus() == TransferStatus.SUCCESS,
                        sp.getTransferStatus().name()
                ))
                .sorted(Comparator.comparing(SettlementParticipantItemDto::userName))
                .toList();

        int paidCount = (int) items.stream().filter(SettlementParticipantItemDto::paid).count();

        return new SettlementPageResponseDto(
                s.getId(),
                s.getStatus(),
                s.getInitiator().getId(),
                s.getInitiator().getName(),
                s.getMealAppointment().getId(),
                s.getMealAppointment().getName(),
                s.getMealAppointment().getAppointmentDate(),
                s.getMealAppointment().getAppointmentTime(),
                s.getTotalAmount(),
                s.getPerHeadAmount(),
                s.getParticipantsCount(),
                paidCount,
                items
        );
    }

    private SettlementPageResponseDto createEmptySettlementPageResponse(MealAppointment mealAppointment) {
        return new SettlementPageResponseDto(
                null, // settlementId
                null, // status
                null, // initiatorId
                null, // initiatorName
                mealAppointment.getId(), // mealAppointmentId
                mealAppointment.getName(), // mealName
                mealAppointment.getAppointmentDate(), // appointmentDate
                mealAppointment.getAppointmentTime(), // appointmentTime
                0, // totalAmount
                0, // perHeadAmount
                0, // participantsCount
                0, // paidCount
                Collections.emptyList() // participants
        );
    }
}
