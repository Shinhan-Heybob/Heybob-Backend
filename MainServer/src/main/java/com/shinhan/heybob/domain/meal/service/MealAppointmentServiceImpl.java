package com.shinhan.heybob.domain.meal.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.meal.dto.request.CreateMealAppointmentRequest;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentDetailResponse;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentListResponse;
import com.shinhan.heybob.domain.meal.dto.response.MealAppointmentStatisticsResponse;
import com.shinhan.heybob.domain.meal.entity.MealAppointment;
import com.shinhan.heybob.domain.meal.entity.MealParticipant;
import com.shinhan.heybob.domain.meal.entity.MealType;
import com.shinhan.heybob.domain.meal.repository.MealAppointmentRepository;
import com.shinhan.heybob.domain.meal.repository.MealParticipantRepository;
import com.shinhan.heybob.domain.notification.service.ChatIntegrationService;
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
    @Transactional
    public MealAppointmentDetailResponse createMealAppointment(CreateMealAppointmentRequest request) {
        validateAppointmentTime(request.getAppointmentDate(), request.getAppointmentTime());
        
        if (request.getParticipantIds() == null || request.getParticipantIds().isEmpty()) {
            throw new HeybobException(ExceptionStatus.INVALID_PARTICIPANT_LIST);
        }

        Long creatorId = request.getCreatorId() != null ? request.getCreatorId() : 1L;
        User creator = userRepository.findById(creatorId)
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
                .type(request.getMealType() != null ? request.getMealType() : MealType.MEAL_APPOINTMENT)
                .build();

        mealAppointment = mealAppointmentRepository.save(mealAppointment);

        for (User participant : participants) {
            MealParticipant mealParticipant = MealParticipant.builder()
                    .user(participant)
                    .build();
            mealAppointment.addParticipant(mealParticipant);
        }

        mealAppointment = mealAppointmentRepository.save(mealAppointment);

        Long chatRoomId = chatIntegrationService.createChatRoom(mealAppointment);
        log.info("채팅방 생성 완료: {}", chatRoomId);
        
        // chatRoomId를 데이터베이스에 저장
        mealAppointment.setChatRoomId(chatRoomId);
        mealAppointment = mealAppointmentRepository.save(mealAppointment);

        return convertToDetailResponse(mealAppointment);
    }

    @Override
    public MealAppointmentDetailResponse getMealAppointment(Long appointmentId) {
        if (appointmentId == null) {
            throw new HeybobException(ExceptionStatus.NOT_FOUND_CHAT_ROOM_ID);
        }

        MealAppointment mealAppointment = mealAppointmentRepository.findByChatRoomId(appointmentId)
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


    private MealAppointmentDetailResponse convertToDetailResponse(MealAppointment mealAppointment) {
        return convertToDetailResponse(mealAppointment, mealAppointment.getChatRoomId());
    }
    
    private MealAppointmentDetailResponse convertToDetailResponse(MealAppointment mealAppointment, Long chatRoomId) {
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
                .mealType(mealAppointment.getType())
                .chatRoomId(chatRoomId)
                .createdAt(mealAppointment.getCreatedAt())
                .build();
    }

    @Override
    public List<MealAppointmentListResponse> getUserMealAppointmentList(Long userId) {
        return getUserMealAppointmentList(userId, "all");
    }

    @Override
    public List<MealAppointmentListResponse> getUserMealAppointmentList(Long userId, String status) {
        if (userId == null) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        if (!userRepository.existsById(userId)) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        List<MealAppointment> appointments = mealAppointmentRepository.findByUserIdWithParticipants(userId);
        LocalDate today = LocalDate.now();
        
        return appointments.stream()
                .sorted((a, b) -> {
                    // 먼저 날짜, 시간 순으로 정렬
                    LocalDateTime aDateTime = LocalDateTime.of(a.getAppointmentDate(), a.getAppointmentTime());
                    LocalDateTime bDateTime = LocalDateTime.of(b.getAppointmentDate(), b.getAppointmentTime());
                    return aDateTime.compareTo(bDateTime);
                })
                .map(appointment -> {
                    // 날짜 기준으로만 active/inactive 판단 (오늘 날짜 포함해서 이후면 active)
                    boolean isActive = !appointment.getAppointmentDate().isBefore(today);
                    
                    User creator = appointment.getCreator();
                    return MealAppointmentListResponse.builder()
                            .id(appointment.getId())
                            .name(appointment.getName())
                            .creatorName(creator.getName())
                            .creatorStudentId(creator.getStudentId())
                            .creatorDepartment(creator.getDepartment())
                            .chatRoomId(appointment.getChatRoomId())
                            .mealType(appointment.getType())
                            .isActive(isActive)
                            .build();
                })
                .filter(appointment -> {
                    if ("active".equalsIgnoreCase(status)) {
                        return appointment.isActive();
                    } else if ("inactive".equalsIgnoreCase(status)) {
                        return !appointment.isActive();
                    }
                    return true; // "all" 또는 다른 값일 경우 모두 반환
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public List<MealAppointmentDetailResponse> getUserMealAppointments(Long userId, MealType type) {
        if (userId == null) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        if (!userRepository.existsById(userId)) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        List<MealAppointment> appointments;
        if (type == null) {
            appointments = mealAppointmentRepository.findByUserIdWithParticipants(userId);
        } else {
            appointments = mealAppointmentRepository.findByUserIdAndTypeWithParticipants(userId, type);
        }
        
        return appointments.stream()
                .map(this::convertToDetailResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<MealAppointmentListResponse> getUserMealAppointmentList(Long userId, String status, MealType type) {
        if (userId == null) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        if (!userRepository.existsById(userId)) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        List<MealAppointment> appointments;
        if (type == null) {
            appointments = mealAppointmentRepository.findByUserIdWithParticipants(userId);
        } else {
            appointments = mealAppointmentRepository.findByUserIdAndTypeWithParticipants(userId, type);
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        return appointments.stream()
                .sorted((a, b) -> {
                    // 먼저 날짜, 시간 순으로 정렬
                    LocalDateTime aDateTime = LocalDateTime.of(a.getAppointmentDate(), a.getAppointmentTime());
                    LocalDateTime bDateTime = LocalDateTime.of(b.getAppointmentDate(), b.getAppointmentTime());
                    return aDateTime.compareTo(bDateTime);
                })
                .map(appointment -> {
                    LocalDateTime appointmentDateTime = LocalDateTime.of(
                            appointment.getAppointmentDate(), 
                            appointment.getAppointmentTime()
                    );
                    boolean isActive = appointmentDateTime.isAfter(now);
                    
                    User creator = appointment.getCreator();
                    return MealAppointmentListResponse.builder()
                            .id(appointment.getId())
                            .name(appointment.getName())
                            .creatorName(creator.getName())
                            .creatorStudentId(creator.getStudentId())
                            .creatorDepartment(creator.getDepartment())
                            .chatRoomId(appointment.getChatRoomId())
                            .mealType(appointment.getType())
                            .isActive(isActive)
                            .build();
                })
                .filter(appointment -> {
                    if ("active".equalsIgnoreCase(status)) {
                        return appointment.isActive();
                    } else if ("inactive".equalsIgnoreCase(status)) {
                        return !appointment.isActive();
                    }
                    return true; // "all" 또는 다른 값일 경우 모두 반환
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public MealAppointmentStatisticsResponse getUserMealAppointmentStatistics(Long userId) {
        if (userId == null) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        if (!userRepository.existsById(userId)) {
            throw new HeybobException(ExceptionStatus.USER_NOT_FOUND);
        }

        long mealAppointmentCount = mealAppointmentRepository.countByUserIdAndType(userId, MealType.MEAL_APPOINTMENT);
        long regularMeetingCount = mealAppointmentRepository.countByUserIdAndType(userId, MealType.REGULAR_MEETING);
        long totalCount = mealAppointmentCount + regularMeetingCount;

        return MealAppointmentStatisticsResponse.builder()
                .userId(userId)
                .mealAppointmentCount(mealAppointmentCount)
                .regularMeetingCount(regularMeetingCount)
                .totalCount(totalCount)
                .build();
    }
    
    @Override
    @Transactional
    public void deleteMealAppointment(Long appointmentId, Long userId) {
        if (appointmentId == null || userId == null) {
            throw new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND);
        }
        
        MealAppointment appointment = mealAppointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND));
        
        // 생성자만 삭제할 수 있도록 권한 체크
        if (!appointment.getCreator().getId().equals(userId)) {
            throw new HeybobException(ExceptionStatus.MEAL_APPOINTMENT_NOT_FOUND);
        }
        
        log.info("🗑️ 밥약 삭제: appointmentId={}, creatorId={}", appointmentId, userId);
        mealAppointmentRepository.delete(appointment);
        log.info("✅ 밥약 삭제 완료: appointmentId={}", appointmentId);
    }
}