package com.shinhan.heybob.domain.savings.service;

public interface SavingsService {

    void createSavingsAccount(Long userId, Long chatId, int perAmount, int totalAmount);

    void paySavingsAccount(Long userId, Long chatId);
}
