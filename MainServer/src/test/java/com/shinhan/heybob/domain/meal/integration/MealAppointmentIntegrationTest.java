package com.shinhan.heybob.domain.meal.integration;

import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.request.ScheduleComparisonRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;
import com.shinhan.heybob.domain.meal.dto.response.ScheduleComparisonResponse;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.meal.service.MealAppointmentService;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
class MealAppointmentIntegrationTest {

    @Autowired
    private MealAppointmentService mealAppointmentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MealAppointmentRepository mealAppointmentRepository;

    private User testUser1;
    private User testUser2;
    private User testUser3;

    @BeforeEach
    void setUp() {
        testUser1 = userRepository.save(User.builder()
                .name("김철수")
                .studentId("2020001")
                .university("신한대학교")
                .department("컴퓨터공학과")
                .profileUrl("profile1.jpg")
                .password("password")
                .build());

        testUser2 = userRepository.save(User.builder()
                .name("이영희")
                .studentId("2020002")
                .university("신한대학교")
                .department("경영학과")
                .profileUrl("profile2.jpg")
                .password("password")
                .build());

        testUser3 = userRepository.save(User.builder()
                .name("박민수")
                .studentId("2020003")
                .university("신한대학교")
                .department("전자공학과")
                .profileUrl("profile3.jpg")
                .password("password")
                .build());
    }

    @Test
    @DisplayName("전체 플로우 테스트: 시간표 비교 -> 밥약 생성 -> 조회")
    void fullFlowTest() {
        ScheduleComparisonRequest compareRequest = ScheduleComparisonRequest.builder()
                .date(LocalDate.now().plusDays(7))
                .participantIds(Arrays.asList(testUser1.getId(), testUser2.getId(), testUser3.getId()))
                .build();

        ScheduleComparisonResponse compareResponse = mealAppointmentService.compareSchedules(compareRequest);
        
        assertThat(compareResponse).isNotNull();
        assertThat(compareResponse.getParticipants()).hasSize(3);
        assertThat(compareResponse.getAvailableSlots()).isNotEmpty();

        CreateMealAppointmentRequest createRequest = CreateMealAppointmentRequest.builder()
                .name("점심 밥약")
                .memo("맛있는 점심 먹어요")
                .appointmentDate(compareRequest.getDate())
                .appointmentTime(LocalTime.of(12, 30))
                .participantIds(Arrays.asList(testUser2.getId(), testUser3.getId()))
                .creatorId(testUser1.getId())
                .build();

        MealAppointmentDetailResponse createResponse = mealAppointmentService.createMealAppointment(createRequest);
        
        assertThat(createResponse).isNotNull();
        assertThat(createResponse.getName()).isEqualTo("점심 밥약");
        assertThat(createResponse.getMemo()).isEqualTo("맛있는 점심 먹어요");
        assertThat(createResponse.getParticipants()).hasSize(2);
        assertThat(createResponse.getChatRoomId()).isNotNull();

        MealAppointmentDetailResponse getResponse = mealAppointmentService.getMealAppointment(createResponse.getId());
        
        assertThat(getResponse).isNotNull();
        assertThat(getResponse.getId()).isEqualTo(createResponse.getId());
        assertThat(getResponse.getName()).isEqualTo("점심 밥약");

        List<MealAppointmentDetailResponse> userAppointments = mealAppointmentService.getUserMealAppointments(testUser2.getId());
        
        assertThat(userAppointments).isNotEmpty();
        assertThat(userAppointments).anyMatch(appointment -> appointment.getName().equals("점심 밥약"));
    }

    @Test
    @DisplayName("여러 밥약 생성 및 조회 테스트")
    void multipleAppointmentsTest() {
        CreateMealAppointmentRequest request1 = CreateMealAppointmentRequest.builder()
                .name("아침 밥약")
                .appointmentDate(LocalDate.now().plusDays(7))
                .appointmentTime(LocalTime.of(8, 0))
                .participantIds(Arrays.asList(testUser2.getId()))
                .creatorId(testUser1.getId())
                .build();

        CreateMealAppointmentRequest request2 = CreateMealAppointmentRequest.builder()
                .name("점심 밥약")
                .appointmentDate(LocalDate.now().plusDays(7))
                .appointmentTime(LocalTime.of(12, 0))
                .participantIds(Arrays.asList(testUser2.getId(), testUser3.getId()))
                .creatorId(testUser1.getId())
                .build();

        CreateMealAppointmentRequest request3 = CreateMealAppointmentRequest.builder()
                .name("저녁 밥약")
                .appointmentDate(LocalDate.now().plusDays(8))
                .appointmentTime(LocalTime.of(18, 0))
                .participantIds(Arrays.asList(testUser3.getId()))
                .creatorId(testUser1.getId())
                .build();

        MealAppointmentDetailResponse response1 = mealAppointmentService.createMealAppointment(request1);
        MealAppointmentDetailResponse response2 = mealAppointmentService.createMealAppointment(request2);
        MealAppointmentDetailResponse response3 = mealAppointmentService.createMealAppointment(request3);

        assertThat(response1.getName()).isEqualTo("아침 밥약");
        assertThat(response2.getName()).isEqualTo("점심 밥약");
        assertThat(response3.getName()).isEqualTo("저녁 밥약");

        List<MealAppointmentDetailResponse> user2Appointments = mealAppointmentService.getUserMealAppointments(testUser2.getId());
        assertThat(user2Appointments).hasSize(2);

        List<MealAppointmentDetailResponse> user3Appointments = mealAppointmentService.getUserMealAppointments(testUser3.getId());
        assertThat(user3Appointments).hasSize(2);

        List<MealAppointment> allAppointments = mealAppointmentRepository.findAll();
        assertThat(allAppointments).hasSize(3);
    }
}