package com.helpdesk.config;

import com.helpdesk.organization.Organization;
import com.helpdesk.organization.OrganizationRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives an inbound key to organizations created before email intake existed.
 */
@Component
@Order(0)
public class InboundKeyBackfill implements ApplicationRunner {

    private final OrganizationRepository organizationRepository;

    public InboundKeyBackfill(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        organizationRepository.findByInboundKeyIsNull().forEach(Organization::rotateInboundKey);
    }
}
