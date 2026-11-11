package com.event.producer.adapter.web.user;

import com.event.producer.domain.user.UserStatus;
import jakarta.validation.constraints.NotNull;

/** JSON body received by the API to set the status of a user. Any of the three statuses is accepted. */
public record UserStatusRequest(@NotNull UserStatus status) {
}
