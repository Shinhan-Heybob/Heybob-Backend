package com.shinhan.heybob.chat.domain.chat.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ChatHistoryResponse {
    private List<ChatMessageResponse> messages;
    private String lastMessageId;        // 마지막 메시지 ID
    private boolean hasMore;             // 더 많은 히스토리가 있는지 여부
    private int totalCount;              // 조회된 메시지 개수
}