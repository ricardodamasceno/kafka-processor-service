package com.event.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point. It only boots Spring; it holds no business logic.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EventsProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventsProcessorApplication.class, args);
    }
}
