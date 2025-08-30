package com.shinhan.heybob.domain.timetable.controller;

import com.shinhan.heybob.domain.timetable.dto.TimetableCompareGetRequestDto;
import com.shinhan.heybob.domain.timetable.dto.TimetableCompareGetResponseDto;
import com.shinhan.heybob.domain.timetable.dto.TimetableCreateRequestDto;
import com.shinhan.heybob.domain.timetable.dto.TimetableGetResponseDto;
import com.shinhan.heybob.domain.timetable.service.TimetableService;
import java.net.URI;
import java.util.List;

import com.shinhan.heybob.domain.user.annotation.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/timetable")
@RequiredArgsConstructor
@RestController
public class TimetableController {

    private final TimetableService timetableService;

    @PostMapping
    public ResponseEntity<Long> createTimetable(@RequestBody TimetableCreateRequestDto timetableCreateRequestDto,
                                                @UserId Long userId) {
        System.out.print(userId);
        Long timetableId = timetableService.createTimetable(timetableCreateRequestDto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(timetableId);
    }

//    @GetMapping("/list")
//    public ResponseEntity<List<TimetableGetResponseDto>> getMyTimetables(@UserId Long userId) {
//        return ResponseEntity.ok(timetableService.getMyTimetables(userId));
//    }

    @GetMapping
    public ResponseEntity<TimetableGetResponseDto> getTimetable(@UserId Long userId) {
        return ResponseEntity.ok(timetableService.getTimeTable(userId));
    }

    @DeleteMapping("/{timeTableId}")
    public ResponseEntity<Void> deleteTimetable(@PathVariable Long timeTableId) {
        timetableService.deleteTimeTable(timeTableId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PostMapping("/compare")
    public ResponseEntity<TimetableCompareGetResponseDto> compareTimetables(@RequestBody TimetableCompareGetRequestDto timetableCompareGetRequestDto){
        return ResponseEntity.ok(timetableService.compareTimetables(timetableCompareGetRequestDto));
    }
}
