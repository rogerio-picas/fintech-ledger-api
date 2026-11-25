package com.rpicas.ledgerapi;

import com.rpicas.ledgerapi.domain.Account;
import com.rpicas.ledgerapi.domain.AuditEventType;
import com.rpicas.ledgerapi.domain.TransactionStatus;
import com.rpicas.ledgerapi.repository.AuditLogRepository;
import com.rpicas.ledgerapi.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that pessimistic locking + atomic transactions prevent double spending
 * under
 * real concurrency, end-to-end over HTTP against PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TransferConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int PARALLEL_REQUESTS = 20;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void twentyParallelTransfers_onlyFiveSucceed_whenBalanceCoversFive() throws Exception {
        // Balance covers exactly 5 transfers of 100.00.
        Account source = createAccount("Alice", "500.0000");
        Account target = createAccount("Bob", "0.0000");

        List<Integer> statuses = fireConcurrently(PARALLEL_REQUESTS,
                i -> () -> postTransfer(UUID.randomUUID().toString(), source.getId(), target.getId(), "100.00")
                        .statusCode());

        Map<Integer, Long> byStatus = statuses.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(byStatus).containsOnlyKeys(201, 422);
        assertThat(byStatus.get(201)).isEqualTo(5L);
        assertThat(byStatus.get(422)).isEqualTo(15L);

        // Money is conserved and the source never goes negative.
        assertThat(balanceOf(source.getId())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balanceOf(target.getId())).isEqualByComparingTo("500.00");

        assertThat(transactionRepository.countBySourceAccountIdAndStatus(source.getId(), TransactionStatus.COMPLETED))
                .isEqualTo(5L);
        assertThat(transactionRepository.countBySourceAccountIdAndStatus(source.getId(), TransactionStatus.FAILED))
                .isEqualTo(15L);

        assertThat(auditLogRepository.countByAccountIdAndEventType(source.getId(), AuditEventType.DEBIT)).isEqualTo(5L);
        assertThat(auditLogRepository.countByAccountIdAndEventType(target.getId(), AuditEventType.CREDIT))
                .isEqualTo(5L);
        assertThat(auditLogRepository.countByAccountIdAndEventType(source.getId(), AuditEventType.TRANSFER_FAILED))
                .isEqualTo(15L);
    }

    @Test
    void parallelRequestsWithSameIdempotencyKey_areProcessedExactlyOnce() throws Exception {
        Account source = createAccount("Carol", "1000.0000");
        Account target = createAccount("Dave", "0.0000");
        String key = UUID.randomUUID().toString();

        List<HttpResponse<String>> responses = fireConcurrently(10,
                i -> () -> postTransfer(key, source.getId(), target.getId(), "100.00"));

        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(201));
        // Every caller receives the very same transfer.
        assertThat(responses.stream().map(r -> r.body()).distinct()).hasSize(1);
        assertThat(responses.stream().filter(r -> r.headers()
                .firstValue("Idempotent-Replayed").orElse("").equals("false"))).hasSize(1);

        assertThat(balanceOf(source.getId())).isEqualByComparingTo("900.00");
        assertThat(balanceOf(target.getId())).isEqualByComparingTo("100.00");
        assertThat(transactionRepository.countBySourceAccountIdAndStatus(source.getId(), TransactionStatus.COMPLETED))
                .isEqualTo(1L);
    }

    @Test
    void opposingParallelTransfers_doNotDeadlock() throws Exception {
        // A->B and B->A at the same time: without deterministic lock ordering this
        // deadlocks.
        Account a = createAccount("Erin", "1000.0000");
        Account b = createAccount("Frank", "1000.0000");

        List<Integer> statuses = fireConcurrently(PARALLEL_REQUESTS, i -> () -> {
            boolean forward = i % 2 == 0;
            return postTransfer(UUID.randomUUID().toString(),
                    forward ? a.getId() : b.getId(), forward ? b.getId() : a.getId(), "10.00").statusCode();
        });

        assertThat(statuses).containsOnly(201);
        assertThat(balanceOf(a.getId())).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(b.getId())).isEqualByComparingTo("1000.00");
    }

    /**
     * Releases all tasks simultaneously through a start gate to maximise
     * contention.
     */
    private static <T> List<T> fireConcurrently(int count, Function<Integer, Callable<T>> taskFactory)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            CountDownLatch ready = new CountDownLatch(count);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<T> task = taskFactory.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
