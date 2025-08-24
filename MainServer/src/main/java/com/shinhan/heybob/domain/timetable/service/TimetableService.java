package com.shinhan.heybob.domain.timetable.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.lecture.dto.LectureDto;
import com.shinhan.heybob.domain.lecture.entity.Lecture;
import com.shinhan.heybob.domain.lecture.repository.LectureRepository;
import com.shinhan.heybob.domain.lecture.service.LectureService;
import com.shinhan.heybob.domain.timetable.dto.TimetableCreateRequestDto;
import com.shinhan.heybob.domain.timetable.dto.TimetableGetResponseDto;
import com.shinhan.heybob.domain.timetable.entity.Timetable;
import com.shinhan.heybob.domain.timetable.repository.TimetableRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final LectureRepository lectureRepository;

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
}
