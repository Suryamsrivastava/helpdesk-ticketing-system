package com.helpdesk.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsBySlug(String slug);

    Optional<Organization> findByInboundKey(String inboundKey);

    List<Organization> findByInboundKeyIsNull();
}
