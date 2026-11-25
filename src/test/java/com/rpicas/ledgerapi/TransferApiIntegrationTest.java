package com.rpicas.ledgerapi;

import com.rpicas.ledgerapi.domain.Account;
import com.rpicas.ledgerapi.domain.AuditEventType;
import com.rpicas.ledgerapi.domain.AuditLog;
import com.rpicas.ledgerapi.domain.TransactionStatus;
import com.rpicas.ledgerapi.repository.AuditLogRepository;
import com.rpicas.ledgerapi.repository.TransactionRepository;
import com.rpicas.ledgerapi.web.dto.AuditLogResponse;
import com.rpicas.ledgerapi.web.dto.TransferResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TransferApiIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private TransactionRepository transactionRepository;

        @Autowired
        private AuditLogRepository auditLogRepository;

        @Autowired
        private JsonMapper jsonMapper;

        @Test
        void retryWithSameKey_replaysOriginalResponse() throws Exception {
                Account source = createAccount("Alice", "300.0000");
                Account target = createAccount("Bob", "0.0000");
                String key = UUID.randomUUID().toString();

                HttpResponse<String> first = postTransfer(key, source.getId(), target.getId(), "100");
                HttpResponse<String> retry = postTransfer(key, source.getId(), target.getId(), "100.00");

                assertThat(first.statusCode()).isEqualTo(201);
                assertThat(first.headers().firstValue("Idempotent-Replayed")).contains("false");
                assertThat(retry.statusCode()).isEqualTo(201);
                assertThat(retry.headers().firstValue("Idempotent-Replayed")).contains("true");
                assertThat(retry.body()).isEqualTo(first.body());
                assertThat(balanceOf(source.getId())).isEqualByComparingTo("200.00");
        }

        @Test
        void insufficientFunds_isCachedAs422() throws Exception {
                Account source = createAccount("Alice", "50.0000");
                Account target = createAccount("Bob", "0.0000");
                String key = UUID.randomUUID().toString();

                HttpResponse<String> first = postTransfer(key, source.getId(), target.getId(), "100.00");
                HttpResponse<String> retry = postTransfer(key, source.getId(), target.getId(), "100.00");

                assertThat(first.statusCode()).isEqualTo(422);
                assertThat(retry.statusCode()).isEqualTo(422);
                assertThat(retry.headers().firstValue("Idempotent-Replayed")).contains("true");
                assertThat(transactionRepository.countBySourceAccountIdAndStatus(source.getId(),
                                TransactionStatus.FAILED))
                                .isEqualTo(1L);
                assertThat(balanceOf(source.getId())).isEqualByComparingTo("50.00");
        }

        @Test
        void sameKeyWithDifferentPayload_returns422() throws Exception {
                Account source = createAccount("Alice", "300.0000");
                Account target = createAccount("Bob", "0.0000");
                String key = UUID.randomUUID().toString();

                assertThat(postTransfer(key, source.getId(), target.getId(), "100.00").statusCode()).isEqualTo(201);
                assertThat(postTransfer(key, source.getId(), target.getId(), "150.00").statusCode()).isEqualTo(422);
                assertThat(balanceOf(source.getId())).isEqualByComparingTo("200.00");
        }

        @Test
        void reversal_restoresBalances_andAppendsAuditWithoutTouchingHistory() throws Exception {
                Account source = createAccount("Alice", "300.0000");
                Account target = createAccount("Bob", "50.0000");

                HttpResponse<String> created = postTransfer(UUID.randomUUID().toString(), source.getId(),
                                target.getId(), "120.50");
                TransferResponse original = jsonMapper.readValue(created.body(), TransferResponse.class);
                List<AuditLog> originalAudit = auditLogRepository.findByTransactionIdOrderByIdAsc(original.id());

                HttpResponse<String> reversed = post("/api/transfers/" + original.id() + "/reverse");
                assertThat(reversed.statusCode()).isEqualTo(201);
                TransferResponse reversal = jsonMapper.readValue(reversed.body(), TransferResponse.class);

                assertThat(reversal.reversalOfId()).isEqualTo(original.id());
                assertThat(reversal.sourceAccountId()).isEqualTo(target.getId());
                assertThat(reversal.targetAccountId()).isEqualTo(source.getId());
                assertThat(reversal.amount()).isEqualByComparingTo("120.50");

                assertThat(balanceOf(source.getId())).isEqualByComparingTo("300.00");
                assertThat(balanceOf(target.getId())).isEqualByComparingTo("50.00");
                assertThat(transactionRepository.findById(original.id()).orElseThrow().getStatus())
                                .isEqualTo(TransactionStatus.REVERSED);

                // Historical audit rows are unchanged; new REVERSAL_* rows were appended.
                List<AuditLog> originalAuditAfter = auditLogRepository.findByTransactionIdOrderByIdAsc(original.id());
                assertThat(originalAuditAfter)
                                .extracting(a -> a.getId(), a -> a.getEventType(), a -> a.getNewBalance())
                                .containsExactlyElementsOf(originalAudit.stream()
                                                .map(a -> org.assertj.core.groups.Tuple.tuple(a.getId(),
                                                                a.getEventType(), a.getNewBalance()))
                                                .toList());
                assertThat(auditLogRepository.findByTransactionIdOrderByIdAsc(reversal.id()))
                                .extracting(a -> a.getEventType())
                                .containsExactly(AuditEventType.REVERSAL_DEBIT, AuditEventType.REVERSAL_CREDIT);

                // Second reversal -> 409; reversing the reversal -> 422.
                assertThat(post("/api/transfers/" + original.id() + "/reverse").statusCode()).isEqualTo(409);
                assertThat(post("/api/transfers/" + reversal.id() + "/reverse").statusCode()).isEqualTo(422);
        }

        @Test
        void errorStatusCodes() throws Exception {
                Account source = createAccount("Alice", "100.0000");

                // 400: missing Idempotency-Key header
                assertThat(postTransfer(null, source.getId(), UUID.randomUUID(), "10.00").statusCode()).isEqualTo(400);
                // 400: invalid amount
                assertThat(postTransfer(UUID.randomUUID().toString(), source.getId(), UUID.randomUUID(), "-5")
                                .statusCode())
                                .isEqualTo(400);
                // 404: unknown target account
                assertThat(postTransfer(UUID.randomUUID().toString(), source.getId(), UUID.randomUUID(), "10.00")
                                .statusCode())
                                .isEqualTo(404);
                // 404: unknown transaction
                assertThat(post("/api/transfers/" + UUID.randomUUID() + "/reverse").statusCode()).isEqualTo(404);
                // 422: same source and target
                assertThat(postTransfer(UUID.randomUUID().toString(), source.getId(), source.getId(), "10.00")
                                .statusCode())
                                .isEqualTo(422);
                // Nothing moved.
                assertThat(balanceOf(source.getId())).isEqualByComparingTo("100.00");
        }

        @Test
        void readOnlyInspectionEndpoints_returnAccountsTransfersAndAuditTrail() throws Exception {
                Account source = createAccount("Alice", "400.0000");
                Account target = createAccount("Bob", "100.0000");

                HttpResponse<String> created = postTransfer(UUID.randomUUID().toString(), source.getId(),
                                target.getId(), "75.00");
                assertThat(created.statusCode()).isEqualTo(201);
                TransferResponse original = jsonMapper.readValue(created.body(), TransferResponse.class);

                HttpResponse<String> reversed = post("/api/transfers/" + original.id() + "/reverse");
                assertThat(reversed.statusCode()).isEqualTo(201);

                HttpResponse<String> accountsResp = get("/api/accounts");
                assertThat(accountsResp.statusCode()).isEqualTo(200);
                assertThat(accountsResp.body()).contains(source.getId().toString(), target.getId().toString());

                HttpResponse<String> transfersResp = get("/api/transfers");
                assertThat(transfersResp.statusCode()).isEqualTo(200);
                assertThat(transfersResp.body()).contains(original.id().toString());

                HttpResponse<String> auditResp = get("/api/accounts/" + source.getId() + "/audit");
                assertThat(auditResp.statusCode()).isEqualTo(200);
                AuditLogResponse[] entries = jsonMapper.readValue(auditResp.body(), AuditLogResponse[].class);
                assertThat(entries)
                                .extracting(AuditLogResponse::eventType)
                                .containsExactly(AuditEventType.DEBIT, AuditEventType.REVERSAL_CREDIT);

                assertThat(get("/api/accounts/" + UUID.randomUUID() + "/audit").statusCode()).isEqualTo(404);
        }

        @Test
        void openApiDocsEndpoint_isExposed() throws Exception {
                HttpResponse<String> response = get("/v3/api-docs");
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains("/api/transfers", "/api/accounts/{id}/audit");
        }
}
