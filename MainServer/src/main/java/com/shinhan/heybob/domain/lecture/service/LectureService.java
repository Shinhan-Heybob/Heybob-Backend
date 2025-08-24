package com.shinhan.heybob.domain.lecture.service;

import com.shinhan.heybob.common.exception.ExceptionStatus;
import com.shinhan.heybob.common.exception.HeybobException;
import com.shinhan.heybob.domain.lecture.dto.LectureCreateRequestDto;
import com.shinhan.heybob.domain.lecture.dto.LectureUpdateRequestDto;
import com.shinhan.heybob.domain.lecture.entity.Lecture;
import com.shinhan.heybob.domain.lecture.repository.LectureRepository;
import com.shinhan.heybob.domain.timetable.entity.Timetable;
import com.shinhan.heybob.domain.timetable.repository.TimetableRepository;
import com.shinhan.heybob.domain.timetable.service.TimetableService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LectureService {

    private final TimetableRepository timetableRepository;
    private final LectureRepository lectureRepository;

    @Transactional
    public void createLecture(LectureCreateRequestDto lectureCreateRequestDto, Long timeTableId) {
        Timetable timetable = timetableRepository.findById(timeTableId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.TIMETABLE_NOT_FOUND));

        if(lectureRepository.existsByDayOfWeekAndTimeOverlap(lectureCreateRequestDto.dayOfWeek(),
                lectureCreateRequestDto.startTime(),
                lectureCreateRequestDto.endTime()))
            throw new HeybobException(ExceptionStatus.DUPLICATED_LECTURE_TIME);

        Lecture lecture = Lecture.builder()
                .name(lectureCreateRequestDto.name())
                .subjectCode(lectureCreateRequestDto.subjectCode())
                .dayOfWeek(lectureCreateRequestDto.dayOfWeek())
                .startTime(lectureCreateRequestDto.startTime())
                .endTime(lectureCreateRequestDto.endTime())
                .classroom(lectureCreateRequestDto.classroom())
                .professor(lectureCreateRequestDto.professor())
                .timetable(timetable)
                .build();
        lectureRepository.save(lecture);
    }

    public void updateLecture(LectureUpdateRequestDto lectureUpdateRequestDto, Long lectureId) {

        if(lectureRepository.existsByDayOfWeekAndTimeOverlap(lectureUpdateRequestDto.dayOfWeek(),
                lectureUpdateRequestDto.startTime(),
                lectureUpdateRequestDto.endTime()))
            throw new HeybobException(ExceptionStatus.DUPLICATED_LECTURE_TIME);

        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new HeybobException(ExceptionStatus.LECTURE_NOT_FOUND));
        lecture.updateLecture(lectureUpdateRequestDto.name(),
                lectureUpdateRequestDto.subjectCode(),
                lectureUpdateRequestDto.dayOfWeek(),
                lectureUpdateRequestDto.startTime(),
                lectureUpdateRequestDto.endTime(),
                lectureUpdateRequestDto.classroom(),
                lectureUpdateRequestDto.professor());
        lectureRepository.save(lecture);
    }
}



