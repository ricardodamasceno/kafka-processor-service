package com.event.producer.config;

import com.event.producer.application.transaction.TransactionService;
import com.event.producer.application.user.UserService;
import com.event.producer.domain.transaction.TransactionRepository;
import com.event.producer.domain.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Composition root: the only place that knows both the application services and their dependencies.
 * <p>
 * The services are plain classes (no Spring annotations) so the application layer stays
 * framework-free. Spring is used here, in the outermost layer, to create them and inject the
 * ports (Spring picks the adapter that implements each port).
 */
@Configuration
class ServiceConfig {

    /** The source of the current time. Services ask the clock instead of calling {@code Instant.now()}, so tests can fix it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TransactionService transactionService(TransactionRepository repository, UserRepository userRepository) {
        return new TransactionService(repository, userRepository);
    }

    @Bean
    UserService userService(UserRepository repository, Clock clock) {
        return new UserService(repository, clock);
    }
}
