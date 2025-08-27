package com.shinhan.heybob.domain.timetable.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.lecture.dto.LectureDto;
import com.shinhan.heybob.domain.lecture.entity.Lecture;
import com.shinhan.heybob.domain.lecture.repository.LectureRepository;
import com.shinhan.heybob.domain.lecture.service.LectureService;
import com.shinhan.heybob.domain.timetable.dto.*;
import com.shinhan.heybob.domain.timetable.entity.Timetable;
import com.shinhan.heybob.domain.timetable.repository.TimetableRepository;
import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;

import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final LectureRepository lectureRepository;
    private final UserRepository userRepository;

    @Transactional
    public void createTimetable(TimetableCreateRequestDto timetableCreateRequestDto) {
        Timetable timetable = Timetable.builder()
                .timeTableName(timetableCreateRequestDto.timeTableName())
                .build();
        timetableRepository.save(timetable);
    }

    public TimetableGetResponseDto getTimeTable(Long timeTableId){
        Timetable timetable = timetableRepository.findById(timeTableId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.TIMETABLE_NOT_FOUND));
        List<Lecture> lectures = lectureRepository.findAllByTimetableId(timeTableId);
        List<LectureDto> lectureDtoList = lectures.stream().map(
                lecture -> LectureDto.builder()
                        .lectureId(lecture.getId())
                        .lectureName(lecture.getName())
                        .subjectCode(lecture.getSubjectCode())
                        .dayOfWeek(lecture.getDayOfWeek())
                        .startTime(lecture.getStartTime())
                        .endTime(lecture.getEndTime())
                        .classroom(lecture.getClassroom())
                        .professor(lecture.getProfessor())
                        .build()
        ).collect(Collectors.toList());

        return TimetableGetResponseDto.builder()
                .id(timetable.getId())
                .timeTableName(timetable.getTimeTableName())
                .lectures(lectureDtoList)
                .build();
    }

    public void deleteTimeTable(Long timeTableId){
        timetableRepository.deleteById(timeTableId);
    }

    public TimetableCompareGetResponseDto compareTimetables(TimetableCompareGetRequestDto timetableCompareGetRequestDto){
        List<Long> userIdList = timetableCompareGetRequestDto.userIdList();
        String targetDate = convertToKoreanDay(timetableCompareGetRequestDto.day());

        Map<Long, String> userNameMap = getUserNameMap(userIdList);

        // 3. 각 사용자의 시간표 ID 조회
        Map<Long, Long> userTimetableMap = getUserTimetableMap(userIdList);

        // 4. 시간표 ID들로 해당 요일 강의 조회
        List<Long> timetableIds = new ArrayList<>(userTimetableMap.values());
        List<Lecture> allLectures = lectureRepository
                .findByTimetableIdInAndDayOfWeek(timetableIds, targetDate);
        // 5. 강의를 사용자별로 그룹핑 (timetableId -> userId 역매핑 필요)
        Map<Long, Long> timetableUserMap = userTimetableMap.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getValue, // timetableId
                        Map.Entry::getKey    // userId
                ));

        // 6. 9시~19시 30분 단위로 TimeslotDto 생성
        List<TimeslotDto> slots = generateTimeslotDtos(userIdList, allLectures, userNameMap, timetableUserMap);
        return TimetableCompareGetResponseDto.builder()
                .timeslots(slots)
                .build();
    }

    private Map<Long, Long> getUserTimetableMap(List<Long> userIds) {
        List<Timetable> timetables = timetableRepository.findByUserIdIn(userIds);

        // 각 사용자별로 최신 시간표 하나씩만 선택
        return timetables.stream()
                .collect(Collectors.groupingBy(
                        timetable -> timetable.getUser().getId(), // User 객체에서 ID 추출
                        Collectors.maxBy(Comparator.comparing(Timetable::getId)) // 최신 시간표
                ))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().isPresent())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,           // userId
                        entry -> entry.getValue().get().getId() // timetableId
                ));
    }


    private List<TimeslotDto> generateTimeslotDtos(
            List<Long> userIds,
            List<Lecture> allLectures,
            Map<Long, String> userNameMap,
            Map<Long, Long> timetableUserMap) {

        List<TimeslotDto> timeslots = new ArrayList<>();
        LocalTime current = LocalTime.of(9, 0);  // 09:00 시작
        final LocalTime end = LocalTime.of(19, 0);     // 19:00 종료

        while (current.isBefore(end)) {
            LocalTime slotEnd = current.plusMinutes(30);

            // 해당 시간에 강의가 있는 사용자들 찾기
            LocalTime finalCurrent = current;
            Set<Long> busyUserIds = allLectures.stream()
                    .filter(lecture -> isTimeOverlapping(lecture, finalCurrent, end))
                    .map(lecture -> timetableUserMap.get(lecture.getTimetable().getId())) // timetableId -> userId 변환
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // 강의가 없는(가능한) 사용자들의 이름 리스트
            List<String> availablePeopleNames = userIds.stream()
                    .filter(userId -> !busyUserIds.contains(userId))
                    .map(userId -> userNameMap.getOrDefault(userId, "Unknown"))
                    .collect(Collectors.toList());

            // TimeslotDto 생성
            timeslots.add(new TimeslotDto(current, slotEnd, availablePeopleNames));

            current = slotEnd;
        }

        return timeslots;
    }

    private boolean isTimeOverlapping(Lecture lecture, LocalTime slotStart, LocalTime slotEnd) {
        return lecture.getStartTime().isBefore(slotEnd) &&
                lecture.getEndTime().isAfter(slotStart);
    }

    private String convertToKoreanDay(LocalDate date){
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        switch (dayOfWeek) {
            case MONDAY: return "월";
            case TUESDAY: return "화";
            case WEDNESDAY: return "수";
            case THURSDAY: return "목";
            case FRIDAY: return "금";
            case SATURDAY: return "토";
            case SUNDAY: return "일";
            default:
                throw new IllegalArgumentException("잘못된 요일: " + dayOfWeek);
        }
    }

    private Map<Long, String> getUserNameMap(List<Long> userIds) {
        return userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(
                        User::getId,
                        User::getName
                ));
    }
}
