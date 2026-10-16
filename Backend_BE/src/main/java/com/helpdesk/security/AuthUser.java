package com.helpdesk.security;

import com.helpdesk.user.Role;
import com.helpdesk.user.User;

/**
 * The authenticated caller. The organization id here is the ONLY tenant id the
 * services use - it comes from the database row behind the token, never from
 * the request body or URL.
 */
public record AuthUser(Long id, Long organizationId, String email, String fullName, Role role) {

    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getOrganization().getId(), user.getEmail(), user.getFullName(), user.getRole());
    }

    public boolean isStaff() {
        return role.isStaff();
    }

    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }

    public boolean isAdmin() {
        return role == Role.ORG_ADMIN;
    }
}
