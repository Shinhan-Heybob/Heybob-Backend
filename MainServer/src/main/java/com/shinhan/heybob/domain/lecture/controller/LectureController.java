package com.shinhan.heybob.domain.lecture.controller;

import com.shinhan.heybob.domain.lecture.dto.LectureCreateRequestDto;
import com.shinhan.heybob.domain.lecture.service.LectureService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
