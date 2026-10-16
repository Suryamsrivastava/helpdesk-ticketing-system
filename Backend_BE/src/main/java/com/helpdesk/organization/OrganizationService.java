package com.helpdesk.organization;

import com.helpdesk.common.exception.ApiException;
import com.helpdesk.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public record OrganizationResponse(Long id, String name, String slug, Instant createdAt) {
    }

    /** What the mailbox script needs: the key and where to send it. */
    public record EmailIntakeResponse(String inboundKey, String endpointPath, String headerName) {
    }

    @Transactional(readOnly = true)
    public OrganizationResponse current(AuthUser me) {
        Organization org = load(me);
        return new OrganizationResponse(org.getId(), org.getName(), org.getSlug(), org.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public EmailIntakeResponse emailIntake(AuthUser me) {
        requireAdmin(me);
        return intake(load(me));
    }

    /** Invalidates the old key at once; the script must be updated with the new one. */
    @Transactional
    public EmailIntakeResponse rotateInboundKey(AuthUser me) {
        requireAdmin(me);
        Organization org = load(me);
        org.rotateInboundKey();
        return intake(org);
    }

    private Organization load(AuthUser me) {
        return organizationRepository.findById(me.organizationId())
                .orElseThrow(() -> ApiException.notFound("Organization not found"));
    }

    private static EmailIntakeResponse intake(Organization org) {
        return new EmailIntakeResponse(org.getInboundKey(), "/api/inbound/email", "X-Inbound-Key");
    }

    private static void requireAdmin(AuthUser me) {
        if (!me.isAdmin()) {
            throw ApiException.forbidden("Only an organization admin can manage email intake");
        }
    }
}
