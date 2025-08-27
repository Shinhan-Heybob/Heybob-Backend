package com.shinhan.heybob.domain.lecture.controller;

import com.shinhan.heybob.domain.lecture.dto.LectureCreateRequestDto;
import com.shinhan.heybob.domain.lecture.dto.LectureUpdateRequestDto;
import com.shinhan.heybob.domain.lecture.service.LectureService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/timetable")
@RequiredArgsConstructor
@RestController
public class LectureController {

    private final LectureService lectureService;

    @PostMapping("/lecture/{timetableId}")
    public ResponseEntity<?> createLecture(@RequestBody LectureCreateRequestDto lectureCreateRequestDto,
                                           @PathVariable Long timetableId) {
        lectureService.createLecture(lectureCreateRequestDto, timetableId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PatchMapping("/{timetableId}/lecture/{lectureId}")
    public ResponseEntity<?> updateLecture(
            @PathVariable Long timetableId,
            @RequestBody LectureUpdateRequestDto lectureUpdateRequestDto,
            @PathVariable Long lectureId) {
        lectureService.updateLecture(timetableId, lectureUpdateRequestDto, lectureId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @DeleteMapping("/{lectureId}")
    public ResponseEntity<?> deleteLecture(@PathVariable Long lectureId) {
        lectureService.deleteLecture(lectureId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

}
