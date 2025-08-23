package com.shinhan.heybob.domain.user.repository;

import com.shinhan.heybob.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findById(Long id);

    boolean existsByStudentIdAndUniversity(String studentId, String university);

    Optional<User> findByUniversityAndStudentId(String university, String studentId);
}
