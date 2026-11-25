package com.rpicas.ledgerapi.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/** Uniform error body returned by every failing endpoint. */
@Schema(description = "Standardized API error response body")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        @Schema(description = "UTC timestamp of the error")
        Instant timestamp,
        @Schema(description = "HTTP status code", example = "422")
        int status,
        @Schema(description = "HTTP status reason phrase", example = "Unprocessable Content")
        String error,
        @Schema(description = "Human-readable error description")
        String message,
        @Schema(description = "Request path that produced the error", example = "/api/transfers")
        String path,
        @Schema(description = "Field-level validation errors when applicable")
        Map<String, String> fieldErrors) {

    public static ApiError of(HttpStatus status, String message, String path) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path, Map.of());
    }

    public static ApiError of(HttpStatus status, String message, String path, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fieldErrors);
    }
}
