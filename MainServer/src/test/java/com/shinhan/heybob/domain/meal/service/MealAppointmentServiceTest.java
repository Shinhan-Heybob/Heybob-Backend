package com.shinhan.heybob.domain.meal.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.request.ScheduleComparisonRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;
import com.shinhan.heybob.domain.meal.dto.response.ScheduleComparisonResponse;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.entity.MealAppointmentStatus;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.meal.repository.MealParticipantRepository;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class MealAppointmentServiceTest {

    @Mock
    private MealAppointmentRepository mealAppointmentRepository;

    @Mock
    private MealParticipantRepository mealParticipantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatIntegrationService chatIntegrationService;

    @InjectMocks
    private MealAppointmentServiceImpl mealAppointmentService;

    private User testUser1;
    private User testUser2;
    private User testUser3;

    @BeforeEach
    void setUp() {
        testUser1 = User.builder()
                .id(1L)
                .name("김철수")
                .studentId("2020001")
                .university("신한대학교")
                .department("컴퓨터공학과")
                .profileUrl("profile1.jpg")
                .password("password")
                .build();

        testUser2 = User.builder()
                .id(2L)
                .name("이영희")
                .studentId("2020002")
                .university("신한대학교")
                .department("경영학과")
                .profileUrl("profile2.jpg")
                .password("password")
                .build();

        testUser3 = User.builder()
                .id(3L)
                .name("박민수")
                .studentId("2020003")
                .university("신한대학교")
                .department("전자공학과")
                .profileUrl("profile3.jpg")
                .password("password")
                .build();
    }

    @Test
    @DisplayName("시간표 비교 - 정상 케이스")
    void compareSchedules_Success() {
        ScheduleComparisonRequest request = ScheduleComparisonRequest.builder()
                .date(LocalDate.now().plusDays(1))
                .participantIds(Arrays.asList(1L, 2L, 3L))
                .build();

        given(userRepository.findByIdIn(request.getParticipantIds()))
                .willReturn(Arrays.asList(testUser1, testUser2, testUser3));

        ScheduleComparisonResponse response = mealAppointmentService.compareSchedules(request);

        assertThat(response).isNotNull();
        assertThat(response.getDate()).isEqualTo(request.getDate());
        assertThat(response.getParticipants()).hasSize(3);
        assertThat(response.getAvailableSlots()).isNotEmpty();
    }

    @Test
    @DisplayName("시간표 비교 - 참여자 목록이 비어있는 경우")
    void compareSchedules_EmptyParticipantList() {
        ScheduleComparisonRequest request = ScheduleComparisonRequest.builder()
                .date(LocalDate.now().plusDays(1))
                .participantIds(Arrays.asList())
                .build();

        assertThatThrownBy(() -> mealAppointmentService.compareSchedules(request))
                .isInstanceOf(HeybobException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_PARTICIPANT_LIST);
    }

    @Test
    @DisplayName("밥약 생성 - 정상 케이스")
    void createMealAppointment_Success() {
        CreateMealAppointmentRequest request = CreateMealAppointmentRequest.builder()
                .name("점심 밥약")
                .memo("맛있는 점심 먹어요")
                .appointmentDate(LocalDate.now().plusDays(1))
                .appointmentTime(LocalTime.of(12, 30))
                .participantIds(Arrays.asList(2L, 3L))
                .build();

        MealAppointment savedAppointment = MealAppointment.builder()
                .id(1L)
                .name(request.getName())
                .memo(request.getMemo())
                .appointmentDate(request.getAppointmentDate())
                .appointmentTime(request.getAppointmentTime())
                .creator(testUser1)
                .status(MealAppointmentStatus.ACTIVE)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser1));
        given(userRepository.findByIdIn(request.getParticipantIds()))
                .willReturn(Arrays.asList(testUser2, testUser3));
        given(mealAppointmentRepository.save(any(MealAppointment.class)))
                .willReturn(savedAppointment);
        given(chatIntegrationService.createChatRoom(any(MealAppointment.class)))
                .willReturn(12345L);

        MealAppointmentDetailResponse response = mealAppointmentService.createMealAppointment(request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo(request.getName());
        assertThat(response.getMemo()).isEqualTo(request.getMemo());
        assertThat(response.getAppointmentDate()).isEqualTo(request.getAppointmentDate());
        assertThat(response.getAppointmentTime()).isEqualTo(request.getAppointmentTime());
        
        verify(mealAppointmentRepository, times(3)).save(any(MealAppointment.class));
        verify(chatIntegrationService).createChatRoom(any(MealAppointment.class));
    }

    @Test
    @DisplayName("밥약 생성 - 과거 시간으로 생성 시도")
    void createMealAppointment_PastTime() {
        CreateMealAppointmentRequest request = CreateMealAppointmentRequest.builder()
                .name("점심 밥약")
                .memo("맛있는 점심 먹어요")
                .appointmentDate(LocalDate.now().minusDays(1))
                .appointmentTime(LocalTime.of(12, 30))
                .participantIds(Arrays.asList(2L, 3L))
                .build();

        assertThatThrownBy(() -> mealAppointmentService.createMealAppointment(request))
                .isInstanceOf(HeybobException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.PAST_APPOINTMENT_TIME);
    }

    @Test
    @DisplayName("밥약 조회 - 정상 케이스")
    void getMealAppointment_Success() {
        MealAppointment appointment = MealAppointment.builder()
                .id(1L)
                .name("점심 밥약")
                .memo("맛있는 점심")
                .appointmentDate(LocalDate.now().plusDays(1))
                .appointmentTime(LocalTime.of(12, 30))
                .creator(testUser1)
                .status(MealAppointmentStatus.ACTIVE)
                .chatRoomId(12345L)
                .build();

        given(mealAppointmentRepository.findByIdWithParticipants(1L))
                .willReturn(Optional.of(appointment));

        MealAppointmentDetailResponse response = mealAppointmentService.getMealAppointment(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("점심 밥약");
        assertThat(response.getChatRoomId()).isEqualTo(12345L);
    }

    @Test
    @DisplayName("밥약 조회 - 존재하지 않는 밥약")
    void getMealAppointment_NotFound() {
        given(mealAppointmentRepository.findByIdWithParticipants(999L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> mealAppointmentService.getMealAppointment(999L))
                .isInstanceOf(HeybobException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("사용자별 밥약 목록 조회 - 정상 케이스")
    void getUserMealAppointments_Success() {
        MealAppointment appointment1 = MealAppointment.builder()
                .id(1L)
                .name("점심 밥약")
                .appointmentDate(LocalDate.now().plusDays(1))
                .appointmentTime(LocalTime.of(12, 30))
                .creator(testUser1)
                .status(MealAppointmentStatus.ACTIVE)
                .build();

        MealAppointment appointment2 = MealAppointment.builder()
                .id(2L)
                .name("저녁 밥약")
                .appointmentDate(LocalDate.now().plusDays(2))
                .appointmentTime(LocalTime.of(18, 0))
                .creator(testUser2)
                .status(MealAppointmentStatus.ACTIVE)
                .build();

        given(userRepository.existsById(1L)).willReturn(true);
        given(mealAppointmentRepository.findByUserIdWithParticipants(1L))
                .willReturn(Arrays.asList(appointment1, appointment2));

        List<MealAppointmentDetailResponse> responses = mealAppointmentService.getUserMealAppointments(1L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getName()).isEqualTo("점심 밥약");
        assertThat(responses.get(1).getName()).isEqualTo("저녁 밥약");
    }

    @Test
    @DisplayName("사용자별 밥약 목록 조회 - 존재하지 않는 사용자")
    void getUserMealAppointments_UserNotFound() {
        given(userRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> mealAppointmentService.getUserMealAppointments(999L))
                .isInstanceOf(HeybobException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.USER_NOT_FOUND);
    }
}