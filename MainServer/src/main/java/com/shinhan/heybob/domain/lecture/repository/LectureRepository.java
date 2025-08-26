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
            "WHERE l.dayOfWeek = :dayOfWeek " +
            "AND l.startTime < :endTime " +    // 새 수업의 종료 이전에 기존 수업이 시작되고
            "AND l.endTime > :startTime")      // 새 수업의 시작 이후에 기존 수업이 종료될 때 겹침
    boolean existsByDayOfWeekAndTimeOverlap(
            @Param("dayOfWeek") String dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    List<Lecture> findAllByTimetableId(Long timetableId);
}
