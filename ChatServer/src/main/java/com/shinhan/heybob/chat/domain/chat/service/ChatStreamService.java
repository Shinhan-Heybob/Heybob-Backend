package com.shinhan.heybob.chat.domain.chat.service;

import com.shinhan.heybob.chat.domain.chat.dto.ChatMessageResponse;

public interface ChatStreamService {
    void saveToStream(ChatMessageResponse message);
}