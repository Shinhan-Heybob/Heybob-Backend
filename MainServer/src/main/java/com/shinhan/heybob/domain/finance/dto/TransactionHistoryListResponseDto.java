package com.shinhan.heybob.domain.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionHistoryListResponseDto {

    private int totalCount;

    private List<TransactionHistoryDto> transactionHistoryDtoList;
}
