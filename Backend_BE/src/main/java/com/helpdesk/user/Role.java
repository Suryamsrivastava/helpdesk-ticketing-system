package com.helpdesk.user;

public enum Role {
    ORG_ADMIN,
    AGENT,
    CUSTOMER;

    /** Admins and agents work tickets; customers only raise and follow their own. */
    public boolean isStaff() {
        return this == ORG_ADMIN || this == AGENT;
    }
}
