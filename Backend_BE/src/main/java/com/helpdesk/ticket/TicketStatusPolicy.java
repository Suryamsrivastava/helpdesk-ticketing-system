package com.helpdesk.ticket;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Which status moves are allowed, and which of them a customer may make.
 *
 * <pre>
 * OPEN        -> IN_PROGRESS, RESOLVED, CLOSED
 * IN_PROGRESS -> OPEN, RESOLVED, CLOSED
 * RESOLVED    -> IN_PROGRESS, OPEN (reopen), CLOSED
 * CLOSED      -> OPEN (reopen)
 * </pre>
 */
public final class TicketStatusPolicy {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            TicketStatus.OPEN, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.IN_PROGRESS, EnumSet.of(TicketStatus.OPEN, TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.RESOLVED, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.OPEN, TicketStatus.CLOSED),
            TicketStatus.CLOSED, EnumSet.of(TicketStatus.OPEN)
    );

    private TicketStatusPolicy() {
    }

    public static boolean canTransition(TicketStatus from, TicketStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** Customers may close their ticket, or reopen one that was resolved or closed. */
    public static boolean customerMay(TicketStatus from, TicketStatus to) {
        if (to == TicketStatus.CLOSED) {
            return true;
        }
        return to == TicketStatus.OPEN && (from == TicketStatus.RESOLVED || from == TicketStatus.CLOSED);
    }
}
