package com.rpicas.ledgerapi.config;

import com.rpicas.ledgerapi.repository.AccountRepository;
import com.rpicas.ledgerapi.service.AccountService;
import com.rpicas.ledgerapi.service.TransferService;
import com.rpicas.ledgerapi.web.dto.AccountResponse;
import com.rpicas.ledgerapi.web.dto.CreateAccountRequest;
import com.rpicas.ledgerapi.web.dto.TransferRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds sample accounts and an initial transfer when running with {@code --spring.profiles.active=demo}
 * so reviewers can immediately explore the API in Swagger UI without manual setup.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final TransferService transferService;

    public DemoDataSeeder(AccountRepository accountRepository,
                          AccountService accountService,
                          TransferService transferService) {
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.transferService = transferService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accountRepository.count() > 0) {
            log.info("Demo profile active: database already contains {} account(s); skipping seed.",
                    accountRepository.count());
            return;
        }

        AccountResponse alice = accountService.create(
                new CreateAccountRequest("Alice Silva", "PT50000201231234567890154", new BigDecimal("1000.0000")));
        AccountResponse bob = accountService.create(
                new CreateAccountRequest("Bob Santos", "PT50000201231234567890251", new BigDecimal("500.0000")));
        AccountResponse charlie = accountService.create(
                new CreateAccountRequest("Charlie Mendes", "PT50000201231234567890348", new BigDecimal("250.0000")));

        transferService.transfer("demo-seed-transfer-001",
                new TransferRequest(alice.id(), bob.id(), new BigDecimal("150.0000")));

        log.info("Demo seed complete — Alice={} (850.0000), Bob={} (650.0000), Charlie={} (250.0000). "
                        + "Explore the API at http://localhost:8080/swagger-ui.html",
                alice.id(), bob.id(), charlie.id());
    }
}
