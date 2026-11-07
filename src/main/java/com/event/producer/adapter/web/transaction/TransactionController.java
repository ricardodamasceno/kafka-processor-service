package com.event.producer.adapter.web.transaction;

import com.event.producer.adapter.web.PaginationProperties;
import com.event.producer.application.transaction.TransactionService;
import com.event.producer.domain.transaction.Transaction;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * HTTP entry point for transactions. Translates HTTP into service calls; holds no business rule.
 * The pagination limits come from {@code application.yml}.
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
class TransactionController {

    private final TransactionService service;
    private final PaginationProperties pagination;

    @PostMapping
    ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        Transaction transaction = service.create(
                request.accountId(), request.type(), request.amount(), request.occurredAt());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(transaction.id()).toUri();
        return ResponseEntity.created(location).body(TransactionResponse.from(transaction));
    }

    @GetMapping("/{id}")
    TransactionResponse get(@PathVariable UUID id) {
        return TransactionResponse.from(service.get(id));
    }

    @GetMapping
    List<TransactionResponse> list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "${app.pagination.default-page-size}") int size) {
        return service.list(pagination.sanitizePage(page), pagination.sanitizeSize(size))
                .stream().map(TransactionResponse::from).toList();
    }

    @PutMapping("/{id}")
    TransactionResponse update(@PathVariable UUID id, @Valid @RequestBody TransactionRequest request) {
        return TransactionResponse.from(service.update(
                id, request.accountId(), request.type(), request.amount(), request.occurredAt()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
