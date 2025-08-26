package com.shinhan.heybob.domain.test.service;

import com.shinhan.heybob.common.chat.dto.ChatBroadcastRequest;

public interface TestService {
    String sendSettlementBroadcast(ChatBroadcastRequest request);
    String sendSavingsBroadcast(ChatBroadcastRequest request);
}