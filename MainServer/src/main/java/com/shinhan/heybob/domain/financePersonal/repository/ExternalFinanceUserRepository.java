package com.shinhan.heybob.domain.financePersonal.repository;

import com.shinhan.heybob.domain.financePersonal.entity.ExternalFinanceUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExternalFinanceUserRepository extends JpaRepository<ExternalFinanceUser, Long> {

    Optional<ExternalFinanceUser> findByUserId(String userId);

    boolean existsByUserId(String userId);

    @Query("select e.id from ExternalFinanceUser e where e.userRealId = :userRealId")
    Optional<Long> findIdByUserRealId(@Param("userRealId") Long userRealId);

    @Query("select e.userKey from ExternalFinanceUser e where e.userRealId = :userRealId")
    Optional<String> findUserKeyByUserRealId(@Param("userRealId") Long userRealId);
}
