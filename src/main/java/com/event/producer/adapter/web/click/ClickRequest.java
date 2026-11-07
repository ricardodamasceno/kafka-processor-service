package com.event.producer.adapter.web.click;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * JSON body received by the API to create or update a click.
 * A DTO (data transfer object) separates the HTTP contract from the domain model, so each can change on its own.
 */
public record ClickRequest(
        @NotBlank String adId,
        @NotBlank String userId,
        @NotNull Instant clickedAt) {
}
