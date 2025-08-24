package com.shinhan.heybob.domain.meal.service;

import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatIntegrationServiceImpl implements ChatIntegrationService {
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Override
    public Long createChatRoom(MealAppointment mealAppointment) {
        log.info("밥약 ID {}에 대한 채팅방 생성 요청 (더미 구현)", mealAppointment.getId());
        
        Long dummyChatRoomId = System.currentTimeMillis() % 1000000;
        
        return dummyChatRoomId;
    }
}