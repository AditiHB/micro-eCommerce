package com.ecommerce.common.dlq;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator tooling for messages this service could not process: list them, and replay one once its cause is
 * fixed. Admin only (see {@code SecurityConfig}); each service exposes the dead letters of its own consumers.
 */
@RestController
@RequestMapping(ApiConstants.API_PREFIX + ApiConstants.DEAD_LETTERS_ENDPOINT)
@RequiredArgsConstructor
@Tag(name = "Dead Letters", description = "Inspect and replay messages that exhausted their retries")
public class DeadLetterController {

    private final DeadLetterService service;

    @GetMapping
    @Operation(summary = "List dead letters", description = "Parked messages of this service's consumers, newest first")
    public ResponseEntity<PagedResponse<DeadLetterResponse>> list(
            @RequestParam(defaultValue = "PARKED") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(status, page, size));
    }

    @PostMapping("/{id}/replay")
    @Operation(summary = "Replay a dead letter", description = "Publishes the parked message to its original topic again")
    public ResponseEntity<DeadLetterResponse> replay(@PathVariable Long id) {
        return ResponseEntity.ok(service.replay(id));
    }
}
