package com.rpicas.ledgerapi;

import com.rpicas.ledgerapi.domain.Account;
import com.rpicas.ledgerapi.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Shared helpers for HTTP-level integration tests against a real server + PostgreSQL. */
abstract class AbstractIntegrationTest {

    @Value("${local.server.port}")
    protected int port;

    @Autowired
    protected AccountRepository accountRepository;

    protected final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    protected Account createAccount(String owner, String balance) {
        return accountRepository.save(new Account(owner, randomIban(), new BigDecimal(balance)));
    }

    protected BigDecimal balanceOf(UUID accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    protected HttpResponse<String> postTransfer(String idempotencyKey, UUID source, UUID target, String amount)
            throws Exception {
        String body = """
                {"sourceAccountId":"%s","targetAccountId":"%s","amount":%s}
                """.formatted(source, target, amount);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/api/transfers"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> post(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    protected static String randomIban() {
        // "PT50" + 21 digits -> matches the IBAN format pattern; uniqueness is what matters here.
        StringBuilder sb = new StringBuilder("PT50");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 21; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
