package com.shinhan.heybob.domain.settlement.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.settlement.dto.SettlementPageResponseDto;
import com.shinhan.heybob.domain.settlement.dto.SettlementParticipantItemDto;
import com.shinhan.heybob.domain.settlement.entity.Settlement;
import com.shinhan.heybob.domain.settlement.model.TransferStatus;
import com.shinhan.heybob.domain.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementQueryService {

    private final SettlementRepository settlementRepository;

    // 채팅방 기준으로 조회하는 버전도 필요하면 제공
    @Transactional(readOnly = true)
    public SettlementPageResponseDto getSettlementPageByChatRoom(Long chatRoomId) {
        Settlement s = settlementRepository.findDetailByChatRoomId(chatRoomId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.SETTLEMENT_NOT_FOUND));
        // 위와 동일 매핑
        // ... 재사용 위해 별도 private mapper로 빼도 좋음
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
}
