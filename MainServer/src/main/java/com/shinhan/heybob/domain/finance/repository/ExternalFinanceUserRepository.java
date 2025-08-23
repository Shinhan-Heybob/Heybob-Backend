package com.shinhan.heybob.domain.finance.repository;

import com.shinhan.heybob.domain.finance.entity.ExternalFinanceUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExternalFinanceUserRepository extends JpaRepository<ExternalFinanceUser, Long> {

    Optional<ExternalFinanceUser> findByUserId(String userId);

    boolean existsByUserId(String userId);
}
