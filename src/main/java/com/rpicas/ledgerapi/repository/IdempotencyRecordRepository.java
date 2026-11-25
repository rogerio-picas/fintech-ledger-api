package com.rpicas.ledgerapi.repository;

import com.rpicas.ledgerapi.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Atomically claims an idempotency key.
     * <p>
     * If another in-flight transaction already inserted the same key, PostgreSQL blocks this
     * statement until that transaction finishes: if it committed, 0 rows are inserted (and the
     * caller can read the stored response); if it rolled back, this insert succeeds.
     *
     * @return 1 if this transaction now owns the key, 0 if it was already taken
     */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records (idempotency_key, request_hash, created_at)
            VALUES (:key, :requestHash, now())
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int tryClaim(@Param("key") String key, @Param("requestHash") String requestHash);
}
