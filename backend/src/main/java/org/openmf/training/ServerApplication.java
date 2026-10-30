package org.openmf.training;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mini Fineract-style server (training). Same idea as {@code org.apache.fineract.ServerApplication}:
 * one Spring Boot process that exposes JAX-RS APIs.
 */
@SpringBootApplication
public class ServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
