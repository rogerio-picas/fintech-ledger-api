package com.rpicas.ledgerapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OpenApiConfig implements WebMvcConfigurer {

    @Bean
    public OpenAPI ledgerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking Ledger & Transfer API")
                        .version("1.0.0")
                        .description("""
                                ACID-compliant banking ledger REST API built with Java 21, Spring Boot 4.2, Flyway, and PostgreSQL 16.

                                ### Key Fintech Invariants
                                - **Exact Monetary Precision:** `BigDecimal` mapped to `NUMERIC(19, 4)` (`RoundingMode.UNNECESSARY`, comparisons via `compareTo`).
                                - **Deadlock-Free Pessimistic Locking:** `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`) acquired in deterministic ascending `UUID` order.
                                - **Atomic Idempotency:** `Idempotency-Key` header fingerprinted via canonical SHA-256 and claimed via `INSERT ... ON CONFLICT DO NOTHING`.
                                - **Append-Only Audit Trail:** `audit_logs` is protected by `@Immutable`, a setter-free entity, no repository delete/update methods, and a PostgreSQL trigger.
                                - **Compensating Reversals:** `POST /api/transfers/{id}/reverse` creates an inverse transaction and appends reversal audit records without altering history.

                                ### Quick Try-It-Out Walkthrough
                                1. `GET /api/accounts` — inspect seeded accounts (when running with `--spring.profiles.active=demo`) or create accounts via `POST /api/accounts`.
                                2. `POST /api/transfers` — execute a transfer with an `Idempotency-Key` header.
                                3. Repeat the exact `POST /api/transfers` call — observe `Idempotent-Replayed: true` and the cached response without double-spending.
                                4. `POST /api/transfers/{id}/reverse` — reverse the transfer cleanly.
                                5. `GET /api/accounts/{id}/audit` — inspect the chronological append-only ledger entries.
                                """));
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/swagger-ui.html");
    }
}
