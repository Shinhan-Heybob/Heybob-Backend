package com.shinhan.heybob.domain.meal.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.request.ScheduleComparisonRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;
import com.shinhan.heybob.domain.meal.dto.response.ScheduleComparisonResponse;
import com.shinhan.heybob.domain.meal.dto.response.TimeSlotDto;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.entity.MealParticipant;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.meal.repository.MealParticipantRepository;
import com.shinhan.heybob.domain.user.dto.UserResponseDto;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MealAppointmentServiceImpl implements MealAppointmentService {

    private final MealAppointmentRepository mealAppointmentRepository;
    private final MealParticipantRepository mealParticipantRepository;
    private final UserRepository userRepository;
    private final ChatIntegrationService chatIntegrationService;

    @Override
    public ScheduleComparisonResponse compareSchedules(ScheduleComparisonRequest request) {
        if (request.getParticipantIds() == null || request.getParticipantIds().isEmpty()) {
            throw new HeybobException(ExceptionStatus.INVALID_PARTICIPANT_LIST);
        }

        List<User> participants = userRepository.findByIdIn(request.getParticipantIds());
        
        if (participants.isEmpty()) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        if (participants.size() != request.getParticipantIds().size()) {
            throw new HeybobException(ExceptionStatus.INVALID_PARTICIPANT_LIST);
        }

        List<UserResponseDto> participantDtos = participants.stream()
                .map(UserResponseDto::new)
                .collect(Collectors.toList());

        List<TimeSlotDto> timeSlots = generateDummyTimeSlots(participants);

        return ScheduleComparisonResponse.builder()
                .date(request.getDate())
                .participants(participantDtos)
                .availableSlots(timeSlots)
                .build();
    }

    @Override
    @Transactional
    public MealAppointmentDetailResponse createMealAppointment(CreateMealAppointmentRequest request) {
        validateAppointmentTime(request.getAppointmentDate(), request.getAppointmentTime());
        
        if (request.getParticipantIds() == null || request.getParticipantIds().isEmpty()) {
            throw new HeybobException(ExceptionStatus.INVALID_PARTICIPANT_LIST);
        }

        User creator = userRepository.findById(1L)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.USER_NOT_FOUND));

        List<User> participants = userRepository.findByIdIn(request.getParticipantIds());
        if (participants.size() != request.getParticipantIds().size()) {
            throw new HeybobException(ExceptionStatus.INVALID_PARTICIPANT_LIST);
        }

        Set<Long> uniqueParticipantIds = new HashSet<>(request.getParticipantIds());
        if (uniqueParticipantIds.size() != request.getParticipantIds().size()) {
            throw new HeybobException(ExceptionStatus.DUPLICATE_PARTICIPANT);
        }

        MealAppointment mealAppointment = MealAppointment.builder()
                .name(request.getName())
                .memo(request.getMemo())
                .appointmentDate(request.getAppointmentDate())
                .appointmentTime(request.getAppointmentTime())
                .creator(creator)
                .build();

        mealAppointment = mealAppointmentRepository.save(mealAppointment);

        for (User participant : participants) {
            MealParticipant mealParticipant = MealParticipant.builder()
                    .user(participant)
                    .build();
            mealAppointment.addParticipant(mealParticipant);
        }

        mealAppointment = mealAppointmentRepository.save(mealAppointment);

        try {
            Long chatRoomId = chatIntegrationService.createChatRoom(mealAppointment);
            
            MealAppointment updatedAppointment = MealAppointment.builder()
                    .id(mealAppointment.getId())
                    .name(mealAppointment.getName())
                    .memo(mealAppointment.getMemo())
                    .appointmentDate(mealAppointment.getAppointmentDate())
                    .appointmentTime(mealAppointment.getAppointmentTime())
                    .creator(mealAppointment.getCreator())
                    .status(mealAppointment.getStatus())
                    .chatRoomId(chatRoomId)
                    .build();
            
            for (MealParticipant participant : mealAppointment.getParticipants()) {
                MealParticipant newParticipant = MealParticipant.builder()
                        .user(participant.getUser())
                        .build();
                updatedAppointment.addParticipant(newParticipant);
            }
            
            mealAppointment = mealAppointmentRepository.save(updatedAppointment);
        } catch (Exception e) {
            log.error("채팅방 생성 실패: ", e);
            throw new HeybobException(ExceptionStatus.CHAT_ROOM_CREATION_FAILED);
        }

        return convertToDetailResponse(mealAppointment);
    }

    @Override
    public MealAppointmentDetailResponse getMealAppointment(Long appointmentId) {
        if (appointmentId == null) {
            throw new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND);
        }

        MealAppointment mealAppointment = mealAppointmentRepository.findByIdWithParticipants(appointmentId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));

        return convertToDetailResponse(mealAppointment);
    }

    @Override
    public List<MealAppointmentDetailResponse> getUserMealAppointments(Long userId) {
        if (userId == null) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        if (!userRepository.existsById(userId)) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        List<MealAppointment> appointments = mealAppointmentRepository.findByUserIdWithParticipants(userId);
        
        return appointments.stream()
                .map(this::convertToDetailResponse)
                .collect(Collectors.toList());
    }

    private void validateAppointmentTime(LocalDate date, LocalTime time) {
        LocalDateTime appointmentDateTime = LocalDateTime.of(date, time);
        LocalDateTime now = LocalDateTime.now();
        
        if (appointmentDateTime.isBefore(now)) {
            throw new HeybobException(ExceptionStatus.PAST_APPOINTMENT_TIME);
        }
    }

    private List<TimeSlotDto> generateDummyTimeSlots(List<User> participants) {
        List<TimeSlotDto> slots = new ArrayList<>();
        LocalTime startTime = LocalTime.of(11, 30);
        LocalTime endTime = LocalTime.of(14, 30);

        List<String> participantNames = participants.stream()
                .map(User::getName)
                .collect(Collectors.toList());

        while (!startTime.isAfter(endTime)) {
            int availableCount = ThreadLocalRandom.current().nextInt(0, participants.size() + 1);
            
            List<String> availableUsers = new ArrayList<>();
            if (availableCount > 0) {
                Collections.shuffle(participantNames);
                availableUsers = participantNames.subList(0, availableCount);
            }

            TimeSlotDto slot = TimeSlotDto.builder()
                    .time(startTime.toString())
                    .availableCount(availableCount)
                    .availableUsers(new ArrayList<>(availableUsers))
                    .isSelectable(availableCount > 0)
                    .build();

            slots.add(slot);
            startTime = startTime.plusMinutes(30);
        }

        return slots;
    }

    private MealAppointmentDetailResponse convertToDetailResponse(MealAppointment mealAppointment) {
        List<UserResponseDto> participantDtos = mealAppointment.getParticipants().stream()
                .map(participant -> new UserResponseDto(participant.getUser()))
                .collect(Collectors.toList());

        return MealAppointmentDetailResponse.builder()
                .id(mealAppointment.getId())
                .name(mealAppointment.getName())
                .memo(mealAppointment.getMemo())
                .appointmentDate(mealAppointment.getAppointmentDate())
                .appointmentTime(mealAppointment.getAppointmentTime())
                .creator(new UserResponseDto(mealAppointment.getCreator()))
                .participants(participantDtos)
                .status(mealAppointment.getStatus().name())
                .chatRoomId(mealAppointment.getChatRoomId())
                .createdAt(mealAppointment.getCreatedAt())
                .build();
    }
}