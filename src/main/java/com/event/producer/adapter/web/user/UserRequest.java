package com.event.producer.adapter.web.user;

import jakarta.validation.constraints.NotBlank;

/**
 * JSON body received by the API to register a user. It only checks that the fields are present: the format and
 * the lengths are rules of the domain {@code User}, which also trims and normalizes them.
 */
public record UserRequest(
        @NotBlank String name,
        @NotBlank String email) {
}
