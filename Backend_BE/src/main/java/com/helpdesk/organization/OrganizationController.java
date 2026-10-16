package com.helpdesk.organization;

import com.helpdesk.organization.OrganizationService.EmailIntakeResponse;
import com.helpdesk.organization.OrganizationService.OrganizationResponse;
import com.helpdesk.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organization")
@Tag(name = "Organization", description = "The caller's own organization")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    @Operation(summary = "My organization")
    public OrganizationResponse current(@AuthenticationPrincipal AuthUser me) {
        return organizationService.current(me);
    }

    @GetMapping("/email-intake")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @Operation(summary = "Inbound key and endpoint for the helpdesk mailbox script (admin)")
    public EmailIntakeResponse emailIntake(@AuthenticationPrincipal AuthUser me) {
        return organizationService.emailIntake(me);
    }

    @PostMapping("/email-intake/rotate")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @Operation(summary = "Replace the inbound key; the old one stops working at once (admin)")
    public EmailIntakeResponse rotateInboundKey(@AuthenticationPrincipal AuthUser me) {
        return organizationService.rotateInboundKey(me);
    }
}
