package com.shinhan.heybob.domain.test.service;

import com.shinhan.heybob.domain.notification.dto.ChatBroadcastRequest;

public interface TestService {
    String sendSettlementBroadcast(ChatBroadcastRequest request);
    String sendSavingsBroadcast(ChatBroadcastRequest request);
    String sendPaymentCompleteBroadcast(ChatBroadcastRequest request);
    String sendSavingsCompleteBroadcast(ChatBroadcastRequest request);
}