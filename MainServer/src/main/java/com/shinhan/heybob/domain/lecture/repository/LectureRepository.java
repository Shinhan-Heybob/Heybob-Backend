package com.shinhan.heybob.domain.lecture.repository;

import com.shinhan.heybob.domain.lecture.entity.Lecture;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LectureRepository extends JpaRepository<Lecture, Long> {

    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END FROM Lecture l " +
            "WHERE l.timetable.id = :timetableId " +     // 특정 시간표에서만 검사
            "AND l.dayOfWeek = :dayOfWeek " +
            "AND l.startTime < :endTime " +
            "AND l.endTime > :startTime")
    boolean existsByTimetableIdAndDayOfWeekAndTimeOverlap(
            @Param("timetableId") Long timetableId,
            @Param("dayOfWeek") String dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    List<Lecture> findAllByTimetableId(Long timetableId);

    List<Lecture> findByTimetableIdInAndDayOfWeek(List<Long> timetableIds, String day);
}
