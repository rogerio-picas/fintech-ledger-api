package com.rpicas.ledgerapi.repository;

import com.rpicas.ledgerapi.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Loads an account with {@code SELECT ... FOR UPDATE}. The row stays locked until the
     * surrounding transaction commits or rolls back. Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByIban(String iban);

    List<Account> findAllByOrderByCreatedAtAsc();
}
