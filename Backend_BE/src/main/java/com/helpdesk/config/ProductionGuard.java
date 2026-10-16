package com.helpdesk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start the prod profile with development defaults, so a forgotten
 * environment variable fails loudly instead of running insecurely.
 */
@Component
@Profile("prod")
public class ProductionGuard {

    static final String DEV_JWT_SECRET = "local-dev-secret-change-me-0123456789-abcdefghijklmnop";

    public ProductionGuard(@Value("${app.jwt.secret}") String jwtSecret,
                           @Value("${app.seed.enabled}") boolean seedEnabled,
                           @Value("${spring.datasource.password:}") String dbPassword) {
        List<String> problems = new ArrayList<>();
        if (DEV_JWT_SECRET.equals(jwtSecret) || jwtSecret.length() < 32) {
            problems.add("JWT_SECRET must be set to a random value of at least 32 characters");
        }
        if (seedEnabled) {
            problems.add("SEED_DEMO_DATA must be false (demo passwords and keys are public)");
        }
        if (dbPassword == null || dbPassword.isBlank()) {
            problems.add("DB_PASSWORD must be set (use a dedicated MySQL user, not root without a password)");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start in prod: " + String.join("; ", problems));
        }
    }
}
