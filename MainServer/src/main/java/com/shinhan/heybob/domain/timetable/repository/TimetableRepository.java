package com.shinhan.heybob.domain.timetable.repository;

import com.shinhan.heybob.domain.timetable.entity.Timetable;
import com.shinhan.heybob.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    List<Timetable> findByUserIdIn(List<Long> userIds);
    Timetable findByUserId(Long userId);
}
