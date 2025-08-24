package com.shinhan.heybob.domain.timetable.controller;

import com.shinhan.heybob.domain.timetable.dto.TimetableCreateRequestDto;
import com.shinhan.heybob.domain.timetable.dto.TimetableGetResponseDto;
import com.shinhan.heybob.domain.timetable.service.TimetableService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/timetable")
@RequiredArgsConstructor
@RestController
public class TimetableController {

    private final TimetableService timetableService;

    @PostMapping
    public ResponseEntity<Void> createTimetable(@RequestBody TimetableCreateRequestDto timetableCreateRequestDto) {
        timetableService.createTimetable(timetableCreateRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{timeTableId}")
    public ResponseEntity<TimetableGetResponseDto> getTimetable(@PathVariable Long timeTableId) {
        return ResponseEntity.ok(timetableService.getTimeTable(timeTableId));
    }
}
