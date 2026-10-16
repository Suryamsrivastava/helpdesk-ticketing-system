package com.helpdesk.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * A tenant. Every user and ticket belongs to exactly one organization.
 */
@Entity
@Table(name = "organizations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_organizations_slug", columnNames = "slug"),
        @UniqueConstraint(name = "uk_organizations_inbound_key", columnNames = "inbound_key")
})
public class Organization {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String slug;

    /**
     * Secret the email-intake script sends with every mail. It is the only thing
     * that decides which tenant an inbound mail belongs to.
     */
    @Column(name = "inbound_key", length = 64)
    private String inboundKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Organization() {
    }

    public Organization(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (inboundKey == null) {
            inboundKey = newInboundKey();
        }
    }

    public static String newInboundKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public void rotateInboundKey() {
        inboundKey = newInboundKey();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getInboundKey() {
        return inboundKey;
    }

    /** Fixed keys are only used for the documented demo organizations. */
    public void setInboundKey(String inboundKey) {
        this.inboundKey = inboundKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
