package com.rpicas.ledgerapi.web;

import com.rpicas.ledgerapi.service.IdempotentResponse;
import com.rpicas.ledgerapi.service.TransferService;
import com.rpicas.ledgerapi.web.dto.ApiError;
import com.rpicas.ledgerapi.web.dto.TransferRequest;
import com.rpicas.ledgerapi.web.dto.TransferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Transfers", description = "Execute idempotent transfers, inspect transactions, and perform compensating reversals")
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * Creates a transfer. Returns 201 on success; a retry with the same Idempotency-Key and
     * payload returns the original status and body with {@code Idempotent-Replayed: true}.
     */
    @Operation(summary = "Execute an idempotent transfer",
            description = "Moves funds atomically between two accounts using deterministic UUID pessimistic locking. "
                    + "Retries with the same Idempotency-Key and canonical payload return the cached status and response "
                    + "with header Idempotent-Replayed: true.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transfer completed (or replayed from idempotency cache)",
                    headers = @Header(name = IDEMPOTENT_REPLAYED_HEADER,
                            description = "true if served from the idempotency cache, false otherwise",
                            schema = @Schema(type = "boolean")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing/invalid Idempotency-Key or invalid request payload",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Source or target account not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Concurrent request with the same Idempotency-Key in progress or lock contention",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "Insufficient funds, same source/target account, or Idempotency-Key reused with different payload",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> transfer(
            @Parameter(description = "Unique client-generated idempotency key (max 255 characters)",
                    required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        IdempotentResponse response = transferService.transfer(idempotencyKey, request);
        return ResponseEntity.status(response.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .header(IDEMPOTENT_REPLAYED_HEADER, String.valueOf(response.replayed()))
                .body(response.body());
    }

    @Operation(summary = "List all transactions", description = "Returns all recorded transfers and reversals ordered newest first.")
    @ApiResponse(responseCode = "200", description = "Transactions retrieved")
    @GetMapping
    public List<TransferResponse> listAll() {
        return transferService.listAll();
    }

    @Operation(summary = "Get transaction by ID", description = "Fetches details of a specific transfer or reversal transaction.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction found"),
            @ApiResponse(responseCode = "400", description = "Invalid UUID format",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Transaction not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public TransferResponse get(
            @Parameter(description = "Transaction UUID") @PathVariable UUID id) {
        return transferService.get(id);
    }

    @Operation(summary = "Reverse a completed transfer",
            description = "Books a compensating inverse transaction, marks the original transaction REVERSED, "
                    + "and appends REVERSAL_DEBIT and REVERSAL_CREDIT audit records without mutating history.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Compensating reversal created"),
            @ApiResponse(responseCode = "400", description = "Invalid UUID format",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Transaction not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Transaction has already been reversed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "Insufficient funds on target account, transaction FAILED, or transaction is itself a reversal",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse reverse(
            @Parameter(description = "Original COMPLETED transaction UUID") @PathVariable UUID id) {
        return transferService.reverse(id);
    }
}
