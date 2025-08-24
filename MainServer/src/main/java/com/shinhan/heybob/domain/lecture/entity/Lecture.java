package com.shinhan.heybob.domain.lecture.entity;

import com.shinhan.heybob.common.entity.BaseTime;
import com.shinhan.heybob.domain.timetable.entity.Timetable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
@RequiredArgsConstructor
@Entity
@Builder
public class Lecture extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String subjectCode;

    private String dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    private String classroom;

    private String professor;

    @ManyToOne
    @JoinColumn(name = "timetable_id")
    private Timetable timetable;
}
