package com.rpicas.ledgerapi.web;

import com.rpicas.ledgerapi.service.AccountService;
import com.rpicas.ledgerapi.web.dto.AccountResponse;
import com.rpicas.ledgerapi.web.dto.ApiError;
import com.rpicas.ledgerapi.web.dto.AuditLogResponse;
import com.rpicas.ledgerapi.web.dto.CreateAccountRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Accounts", description = "Create and inspect bank accounts and their append-only audit trails")
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Create an account", description = "Opens a new ledger account with a unique IBAN and non-negative initial balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Invalid input payload or IBAN format",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Account with this IBAN already exists",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.create(request);
    }

    @Operation(summary = "List all accounts", description = "Returns all accounts ordered chronologically by creation time.")
    @ApiResponse(responseCode = "200", description = "Accounts retrieved")
    @GetMapping
    public List<AccountResponse> listAll() {
        return accountService.listAll();
    }

    @Operation(summary = "Get account by ID", description = "Fetches account metadata and current NUMERIC(19, 4) balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found"),
            @ApiResponse(responseCode = "400", description = "Invalid UUID format",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public AccountResponse get(
            @Parameter(description = "Account UUID") @PathVariable UUID id) {
        return accountService.get(id);
    }

    @Operation(summary = "Get account audit trail",
            description = "Returns the immutable append-only audit log entries for an account in chronological order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit trail retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid UUID format",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}/audit")
    public List<AuditLogResponse> getAuditTrail(
            @Parameter(description = "Account UUID") @PathVariable UUID id) {
        return accountService.getAuditTrail(id);
    }
}
