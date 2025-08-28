package com.shinhan.heybob.domain.financePersonal.repository;

import com.shinhan.heybob.domain.financePersonal.entity.PersonalAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PersonalAccountRepository extends JpaRepository<PersonalAccount, Long> {

    @Query("select p.accountNo from PersonalAccount p where p.externalFinanceUserId = :externalFinanceUserId")
    Optional<String> findAccountNoByExternalFinanceUserId(@Param("externalFinanceUserId") Long externalFinanceUserId);

    @Query("select p.externalFinanceUserId from PersonalAccount p where p.accountNo =:accountNo")
    Optional<Long> findExternalFinanceUserIdByAccountNo(@Param("accountNo")String accountNo);
}
