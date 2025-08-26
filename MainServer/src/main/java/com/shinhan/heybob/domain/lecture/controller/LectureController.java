package com.shinhan.heybob.domain.lecture.controller;

import com.shinhan.heybob.domain.lecture.dto.LectureCreateRequestDto;
import com.shinhan.heybob.domain.lecture.dto.LectureUpdateRequestDto;
import com.shinhan.heybob.domain.lecture.service.LectureService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/timetable/lecture")
@RequiredArgsConstructor
@RestController
public class LectureController {

    private final LectureService lectureService;

    @PostMapping("/{timeTableId}")
    public ResponseEntity<?> createLecture(@RequestBody LectureCreateRequestDto lectureCreateRequestDto,
                                           @PathVariable Long timeTableId) {
        lectureService.createLecture(lectureCreateRequestDto, timeTableId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PatchMapping("/{lectureId}")
    public ResponseEntity<?> updateLecture(@RequestBody LectureUpdateRequestDto lectureUpdateRequestDto,
                                           @PathVariable Long lectureId) {
        lectureService.updateLecture(lectureUpdateRequestDto, lectureId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @DeleteMapping("/{lectureId}")
    public ResponseEntity<?> deleteLecture(@PathVariable Long lectureId) {
        lectureService.deleteLecture(lectureId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

}
