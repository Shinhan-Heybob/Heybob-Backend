package com.shinhan.heybob.domain.meal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.request.ScheduleComparisonRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;
import com.shinhan.heybob.domain.meal.dto.response.ScheduleComparisonResponse;
import com.shinhan.heybob.domain.meal.dto.response.TimeSlotDto;
import com.shinhan.heybob.domain.meal.service.MealAppointmentService;
import com.shinhan.heybob.domain.user.dto.UserResponseDto;
import com.shinhan.heybob.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MealAppointmentController.class)
class MealAppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MealAppointmentService mealAppointmentService;

    private ScheduleComparisonResponse scheduleComparisonResponse;
    private MealAppointmentDetailResponse mealAppointmentDetailResponse;

    @BeforeEach
    void setUp() {
        User testUser = User.builder()
                .id(1L)
                .name("김철수")
                .studentId("2020001")
                .university("신한대학교")
                .department("컴퓨터공학과")
                .profileUrl("profile1.jpg")
                .password("password")
                .build();

        UserResponseDto userDto = new UserResponseDto(testUser);

        TimeSlotDto timeSlot1 = TimeSlotDto.builder()
                .time("12:00")
                .availableCount(3)
                .availableUsers(Arrays.asList("김철수", "이영희", "박민수"))
                .isSelectable(true)
                .build();

        TimeSlotDto timeSlot2 = TimeSlotDto.builder()
                .time("12:30")
                .availableCount(2)
                .availableUsers(Arrays.asList("김철수", "이영희"))
                .isSelectable(true)
                .build();

        scheduleComparisonResponse = ScheduleComparisonResponse.builder()
                .date(LocalDate.now().plusDays(1))
                .participants(Arrays.asList(userDto))
                .availableSlots(Arrays.asList(timeSlot1, timeSlot2))
                .build();

        mealAppointmentDetailResponse = MealAppointmentDetailResponse.builder()
                .id(1L)
                .name("점심 밥약")
                .memo("맛있는 점심")
                .appointmentDate(LocalDate.now().plusDays(1))
                .appointmentTime(LocalTime.of(12, 30))
                .creator(userDto)
                .participants(Arrays.asList(userDto))
                .status("ACTIVE")
                .chatRoomId(12345L)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("시간표 비교 API 테스트")
    @WithMockUser
    void compareSchedules() throws Exception {
        ScheduleComparisonRequest request = ScheduleComparisonRequest.builder()
                .date(LocalDate.now().plusDays(1))
                .participantIds(Arrays.asList(1L, 2L, 3L))
                .build();

        given(mealAppointmentService.compareSchedules(any(ScheduleComparisonRequest.class)))
                .willReturn(scheduleComparisonResponse);

        mockMvc.perform(post("/api/meal-appointments/schedules/compare")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(request.getDate().toString()))
                .andExpect(jsonPath("$.participants").isArray())
                .andExpect(jsonPath("$.availableSlots").isArray())
                .andExpect(jsonPath("$.availableSlots[0].time").value("12:00"))
                .andExpect(jsonPath("$.availableSlots[0].isSelectable").value(true));
    }

    @Test
    @DisplayName("밥약 생성 API 테스트")
    @WithMockUser
    void createMealAppointment() throws Exception {
        CreateMealAppointmentRequest request = CreateMealAppointmentRequest.builder()
                .name("점심 밥약")
                .memo("맛있는 점심")
                .appointmentDate(LocalDate.now().plusDays(1))
                .appointmentTime(LocalTime.of(12, 30))
                .participantIds(Arrays.asList(2L, 3L))
                .build();

        given(mealAppointmentService.createMealAppointment(any(CreateMealAppointmentRequest.class)))
                .willReturn(mealAppointmentDetailResponse);

        mockMvc.perform(post("/api/meal-appointments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("점심 밥약"))
                .andExpect(jsonPath("$.memo").value("맛있는 점심"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.chatRoomId").value(12345));
    }

    @Test
    @DisplayName("밥약 상세 조회 API 테스트")
    @WithMockUser
    void getMealAppointment() throws Exception {
        given(mealAppointmentService.getMealAppointment(1L))
                .willReturn(mealAppointmentDetailResponse);

        mockMvc.perform(get("/api/meal-appointments/1")
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("점심 밥약"))
                .andExpect(jsonPath("$.chatRoomId").value(12345));
    }

    @Test
    @DisplayName("사용자별 밥약 목록 조회 API 테스트")
    @WithMockUser
    void getUserMealAppointments() throws Exception {
        List<MealAppointmentDetailResponse> responses = Arrays.asList(mealAppointmentDetailResponse);
        
        given(mealAppointmentService.getUserMealAppointments(1L))
                .willReturn(responses);

        mockMvc.perform(get("/api/meal-appointments")
                        .param("userId", "1")
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("점심 밥약"));
    }

    @Test
    @DisplayName("밥약 생성 - 필수값 누락 시 검증 실패")
    @WithMockUser
    void createMealAppointment_ValidationFail() throws Exception {
        CreateMealAppointmentRequest request = CreateMealAppointmentRequest.builder()
                .memo("메모만 있음")
                .build();

        mockMvc.perform(post("/api/meal-appointments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}