package com.rpicas.ledgerapi.repository;

import com.rpicas.ledgerapi.domain.Transaction;
import com.rpicas.ledgerapi.domain.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Locks the transaction row so two concurrent reversals of it are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") UUID id);

    long countBySourceAccountIdAndStatus(UUID sourceAccountId, TransactionStatus status);

    Optional<Transaction> findByReversalOfId(UUID reversalOfId);

    List<Transaction> findAllByOrderByCreatedAtDesc();
}
