package com.event.producer.config;

import com.event.producer.application.click.ClickEventPublisher;
import com.event.producer.application.click.ClickService;
import com.event.producer.application.transaction.TransactionEventPublisher;
import com.event.producer.application.transaction.TransactionService;
import com.event.producer.domain.click.ClickRepository;
import com.event.producer.domain.transaction.TransactionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root: the only place that knows both the application services and their dependencies.
 * <p>
 * The services are plain classes (no Spring annotations) so the application layer stays
 * framework-free. Spring is used here, in the outermost layer, to create them and inject the
 * ports (Spring picks the adapter that implements each port).
 */
@Configuration
class ServiceConfig {

    @Bean
    ClickService clickService(ClickRepository repository, ClickEventPublisher publisher) {
        return new ClickService(repository, publisher);
    }

    @Bean
    TransactionService transactionService(TransactionRepository repository, TransactionEventPublisher publisher) {
        return new TransactionService(repository, publisher);
    }
}
