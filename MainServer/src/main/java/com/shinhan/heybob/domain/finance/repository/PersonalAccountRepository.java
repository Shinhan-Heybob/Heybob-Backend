package com.shinhan.heybob.domain.finance.repository;

import com.shinhan.heybob.domain.finance.entity.PersonalAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PersonalAccountRepository extends JpaRepository<PersonalAccount, Long> {

    Optional<String> findAccountNoByExternalFinanceUserId (Long externalFinanceUserId);
}
