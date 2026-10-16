package com.helpdesk.support;

import com.helpdesk.organization.Organization;
import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketPriority;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Builds entities with ids already set, for pure unit tests without a database.
 */
public final class TestData {

    private TestData() {
    }

    public static Organization org(long id) {
        Organization org = new Organization("Org " + id, "org-" + id);
        ReflectionTestUtils.setField(org, "id", id);
        return org;
    }

    public static User user(Organization org, long id, Role role) {
        User user = new User(org, "user" + id + "@example.test", "hash", "User " + id, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    public static Ticket ticket(Organization org, long id, User requester) {
        Ticket ticket = new Ticket(org, requester, "Subject " + id, "Description " + id, TicketPriority.MEDIUM);
        ReflectionTestUtils.setField(ticket, "id", id);
        return ticket;
    }
}
