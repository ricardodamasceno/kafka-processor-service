package com.event.producer.adapter.web.user;

import com.event.producer.application.user.UserService;
import com.event.producer.domain.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** HTTP entry point for users. Translates HTTP into service calls; holds no business rule. */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
class UserController {

    private final UserService service;

    /**
     * Registration is synchronous, so the answer is 201 (created) with the user. Unlike creating a transaction,
     * the body carries the id: the client needs it to create transactions for this user.
     */
    @PostMapping
    ResponseEntity<UserResponse> register(@Valid @RequestBody UserRequest request) {
        User user = service.register(request.name(), request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    /**
     * Sets the status of a user: {@code ACTIVE}, {@code INACTIVE} or {@code BLOCKED}, from any status to any other.
     * The answer is 200 with the user, or 404 when the user does not exist.
     */
    @PutMapping("/{id}/status")
    UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UserStatusRequest request) {
        return UserResponse.from(service.updateStatus(id, request.status()));
    }
}
