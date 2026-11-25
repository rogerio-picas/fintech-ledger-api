package com.rpicas.ledgerapi.repository;

import com.rpicas.ledgerapi.domain.AuditEventType;
import com.rpicas.ledgerapi.domain.AuditLog;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Deliberately extends the bare {@link Repository} marker instead of {@code JpaRepository}
 * so that no delete/update methods are exposed: the audit trail is append-only.
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    List<AuditLog> findByTransactionIdOrderByIdAsc(UUID transactionId);

    List<AuditLog> findByAccountIdOrderByIdAsc(UUID accountId);

    long countByAccountId(UUID accountId);

    long countByAccountIdAndEventType(UUID accountId, AuditEventType eventType);
}
