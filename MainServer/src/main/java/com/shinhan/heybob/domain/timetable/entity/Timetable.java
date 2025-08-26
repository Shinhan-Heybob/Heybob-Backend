package com.shinhan.heybob.domain.timetable.entity;

import com.shinhan.heybob.common.entity.BaseTime;
import com.shinhan.heybob.domain.lecture.entity.Lecture;
import com.shinhan.heybob.domain.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Entity
@AllArgsConstructor
@RequiredArgsConstructor
@Getter
@Builder
public class Timetable extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String timeTableName;

    @OneToMany(mappedBy = "timetable", cascade = CascadeType.ALL)
    private List<Lecture> lectureList = new ArrayList<>();
}
