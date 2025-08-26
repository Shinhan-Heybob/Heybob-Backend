package com.shinhan.heybob.domain.savings.service;

public interface SavingsService {

    void createSavingsAccount(Long userId, Long mealId, int perAmount, int totalAmount);

}
