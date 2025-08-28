package com.shinhan.heybob.domain.user.repository;

import com.shinhan.heybob.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findById(Long id);

    boolean existsByStudentIdAndUniversity(String studentId, String university);

    Optional<User> findByUniversityAndStudentId(String university, String studentId);
    
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.studentId) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.department) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<User> searchUsers(@Param("keyword") String keyword);
    
    List<User> findByIdIn(List<Long> ids);

    @Query("select u.name from User u where u.id = :id")
    Optional<String> findNameById(@Param("id") Long id);
}
