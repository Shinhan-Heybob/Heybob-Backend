package com.shinhan.heybob.domain.meal.integration;

import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.service.MealAppointmentService;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.h2.console.enabled=false"
})
class SimpleMealAppointmentTest {

    @Autowired
    private MealAppointmentService mealAppointmentService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testBasicFlow() {
        try {
            // 1. 사용자 생성
            User testUser1 = userRepository.save(User.builder()
                    .name("김철수")
                    .studentId("2020001")
                    .university("신한대학교")
                    .department("컴퓨터공학과")
                    .profileUrl("profile1.jpg")
                    .password("password")
                    .build());

            User testUser2 = userRepository.save(User.builder()
                    .name("이영희")
                    .studentId("2020002")
                    .university("신한대학교")
                    .department("경영학과")
                    .profileUrl("profile2.jpg")
                    .password("password")
                    .build());

            System.out.println("User1 ID: " + testUser1.getId());
            System.out.println("User2 ID: " + testUser2.getId());

            // 2. 밥약 생성 요청
            CreateMealAppointmentRequest request = CreateMealAppointmentRequest.builder()
                    .name("점심 밥약")
                    .memo("맛있는 점심")
                    .appointmentDate(LocalDate.now().plusDays(7))
                    .appointmentTime(LocalTime.of(12, 30))
                    .participantIds(Arrays.asList(testUser2.getId()))
                    .creatorId(testUser1.getId())
                    .build();

            System.out.println("Creating meal appointment...");
            var response = mealAppointmentService.createMealAppointment(request);
            System.out.println("Success! Meal appointment created with ID: " + response.getId());
            
        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getClass().getSimpleName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}