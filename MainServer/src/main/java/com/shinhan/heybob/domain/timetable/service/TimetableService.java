package com.shinhan.heybob.domain.timetable.service;

import com.shinhan.heybob.domain.timetable.dto.TimetableCreateRequestDto;
import com.shinhan.heybob.domain.timetable.entity.Timetable;
import com.shinhan.heybob.domain.timetable.repository.TimetableRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableRepository timetableRepository;

    @Transactional
    public void createTimetable(TimetableCreateRequestDto timetableCreateRequestDto) {
        Timetable timetable = Timetable.builder()
                .timeTableName(timetableCreateRequestDto.timeTableName())
                .build();
        timetableRepository.save(timetable);
    }
}
