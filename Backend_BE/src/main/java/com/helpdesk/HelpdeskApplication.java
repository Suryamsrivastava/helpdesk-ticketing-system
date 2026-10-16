package com.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// JWT auth is custom, so Spring's default in-memory user is not needed
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HelpdeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelpdeskApplication.class, args);
    }
}
