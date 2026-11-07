package com.event.producer.adapter.web.click;

import com.event.producer.adapter.web.PaginationProperties;
import com.event.producer.application.click.ClickService;
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

import java.util.List;
import java.util.UUID;

/**
 * HTTP entry point for clicks. It only translates HTTP into service calls and back:
 * no business rule lives here. The pagination limits come from {@code application.yml}.
 */
@RestController
@RequestMapping("/clicks")
@RequiredArgsConstructor
class ClickController {

    private final ClickService service;
    private final PaginationProperties pagination;

    /**
     * Creation is asynchronous: the request is validated and published to Kafka, and a consumer
     * writes the click later. So the answer is 202 (accepted), not 201 (created), with an empty body.
     */
    @PostMapping
    ResponseEntity<Void> create(@Valid @RequestBody ClickRequest request) {
        service.requestCreation(request.adId(), request.userId(), request.clickedAt());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}")
    ClickResponse get(@PathVariable UUID id) {
        return ClickResponse.from(service.get(id));
    }

    @GetMapping
    List<ClickResponse> list(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "${app.pagination.default-page-size}") int size) {
        return service.list(pagination.sanitizePage(page), pagination.sanitizeSize(size))
                .stream().map(ClickResponse::from).toList();
    }

    @PutMapping("/{id}")
    ClickResponse update(@PathVariable UUID id, @Valid @RequestBody ClickRequest request) {
        return ClickResponse.from(service.update(id, request.adId(), request.userId(), request.clickedAt()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
